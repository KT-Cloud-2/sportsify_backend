package com.sportsify.chat.integration;

import com.sportsify.chat.infrastructure.webSocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * [동시성] WebSocketSessionRegistry 동시성 통합 테스트
 * <p>
 * Spring 컨텍스트 없이 직접 인스턴스를 생성해 실제 멀티스레드 경쟁을 검증한다.
 * clientInboundChannel은 @Lazy 주입 필드라 null이지만 forceUnsubscribeFromBroker의
 * try-catch에서 처리되므로 테스트 실행에 영향을 주지 않는다.
 */
@DisplayName("[동시성] WebSocketSessionRegistry 동시성 통합 테스트")
class WebSocketSessionRegistryConcurrencyTest {

    private static final String ROLE = "USER";
    private static final Instant FUTURE_EXPIRY = Instant.now().plusSeconds(3600);
    private static final Instant CONNECTED_AT = Instant.now();

    private ApplicationEventPublisher eventPublisher;
    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        registry = new WebSocketSessionRegistry(eventPublisher, Clock.systemDefaultZone());
    }

    /**
     * 왜 동시성 테스트가 필요한가:
     * - subscribeRoom은 두 단계 비원자 연산:
     * roomSessions.add(sid) → info.subscribedRooms.put(subscriptionId, roomId)
     * - 두 단계 사이에 다른 스레드가 끼어들면 한쪽 인덱스에만 반영될 수 있음
     * <p>
     * 실패 가능 포인트:
     * - roomSessions에 sid가 있지만 subscribedRooms에 방이 없는 경우:
     * revokeAll이 해당 세션 구독을 찾지 못해 roomSessions 정리가 누락됨
     */
    @Test
    @DisplayName("N개 세션이 동시에 같은 방을 구독하면 roomSessions와 subscribedRooms 인덱스가 일관성을 유지한다")
    void 동시_방_구독_인덱스_일관성() throws Exception {
        int threadCount = 30;
        long roomId = 9001L;
        String[] sids = new String[threadCount];

        // Given
        for (int i = 0; i < threadCount; i++) {
            sids[i] = "concurrent-sid-" + i;
            WebSocketSession ws = mock(WebSocketSession.class);
            lenient().when(ws.isOpen()).thenReturn(true);
            registry.register(sids[i], ws, (long) (9000 + i), ROLE, FUTURE_EXPIRY, CONNECTED_AT);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            final String sid = sids[i];
            final String subId = "sub-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    registry.subscribeRoom(sid, subId, roomId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Then - roomSessions에 있으면 subscribedRooms에도 있어야 한다
        Set<String> roomMembers = getRoomSessions(roomId);
        for (String sid : sids) {
            boolean inRoomSessions = roomMembers != null && roomMembers.contains(sid);
            boolean inSubscribedRooms = registry.get(sid)
                    .map(info -> info.subscribedRooms().containsValue(roomId))
                    .orElse(false);
            assertThat(inRoomSessions)
                    .as("sid=%s: roomSessions와 subscribedRooms 불일치", sid)
                    .isEqualTo(inSubscribedRooms);
            assertThat(inRoomSessions).as("sid=%s: 구독이 성공해야 한다", sid).isTrue();
        }
    }

    /**
     * 왜 동시성 테스트가 필요한가:
     * - evictExpiredSessions는 만료 세션을 snapshot으로 수집한 뒤 forceDisconnect를 호출
     * - snapshot 수집 이후, forceDisconnect 이전에 updateExpiry가 실행되면:
     * toDisconnect 목록에 들어간 세션이 토큰 갱신 이후에도 종료될 수 있음
     * - phase2의 sessions.get(sid) 재확인이 이 레이스를 방어하는지 검증
     * <p>
     * 실패 가능 포인트:
     * - phase2에서 current.tokenExpiresAt 재확인 없이 forceDisconnect하면
     * updateExpiry로 토큰을 갱신한 세션이 강제 종료됨
     */
    @Test
    @DisplayName("evictExpiredSessions 실행 도중 updateExpiry가 완료되면 세션이 강제 종료되지 않는다")
    void evictExpiredSessions_updateExpiry_동시_실행_세션_유지() throws Exception {
        // Given
        String sid = "expiry-race-sid";
        WebSocketSession ws = mock(WebSocketSession.class);
        lenient().when(ws.isOpen()).thenReturn(true);
        registry.register(sid, ws, 9100L, ROLE, Instant.now().minusSeconds(1), CONNECTED_AT);
        registry.evictExpiredSessions();   // phase1: grace 진입
        setGraceDeadlineToPast(sid);       // grace deadline을 과거로 설정해 phase2 조건 충족

        Instant newExpiry = Instant.now().plusSeconds(7200);
        int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        // When
        executor.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) registry.evictExpiredSessions();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        executor.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) registry.updateExpiry(sid, newExpiry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Then
        registry.updateExpiry(sid, newExpiry);
        reset(ws);
        lenient().when(ws.isOpen()).thenReturn(true);
        registry.evictExpiredSessions();
        verify(ws, never()).close(any(CloseStatus.class));
    }

    /**
     * 왜 동시성 테스트가 필요한가:
     * - forceDisconnect의 null-check(sessions.get)와 ws.close() 호출이 비원자적
     * - 여러 스레드가 null-check를 동시에 통과하면 ws.close()가 중복 호출됨
     * - onSessionEnded의 sessions.remove()는 원자적이므로 정리는 한 번만 실행됨을 검증
     * <p>
     * 실패 가능 포인트:
     * - ws.close() 중복 호출 시 예외가 catch 블록 밖으로 전파되면 스레드가 비정상 종료됨
     * - onSessionEnded에 멱등성이 없으면 roomSessions 등 인덱스가 손상될 수 있음
     */
    @Test
    @DisplayName("동일 세션에 forceDisconnect가 동시에 여러 번 호출되어도 예외가 전파되지 않고 세션이 정리된다")
    void forceDisconnect_동시_다중_호출_안전성() throws Exception {
        // Given
        String sid = "force-disconnect-sid";
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        registry.register(sid, ws, 9200L, ROLE, FUTURE_EXPIRY, CONNECTED_AT);
        registry.subscribeRoom(sid, "sub-fd-1", 9200L);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    registry.forceDisconnect(sid, "concurrent-test");
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Then
        assertThat(exceptionCount.get()).as("예외가 스레드 밖으로 전파되면 안 된다").isZero();
        assertThat(registry.get(sid)).isEmpty();
        Set<String> roomMembers = getRoomSessions(9200L);
        assertThat(roomMembers == null || !roomMembers.contains(sid))
                .as("roomSessions 인덱스도 정리되어야 한다").isTrue();
    }

    /**
     * 왜 동시성 테스트가 필요한가:
     * - subscribeRoom의 두 단계(roomSessions.add, subscribedRooms.put) 사이에
     * revokeAllRoomSubscriptions의 snapshot이 끼어들 수 있음
     * - snapshot에서 sid를 발견하지만 subscribedRooms에 항목이 없어 revoke가 무시되고,
     * 이후 subscribedRooms.put이 완료되면 구독 해제 없이 세션이 구독 상태로 남음
     * <p>
     * 실패 가능 포인트:
     * - roomSessions에 sid가 있지만 subscribedRooms에 roomId가 없는 경우 (또는 반대):
     * revokeAll이 구독 해제를 누락하거나 인덱스 불일치로 이후 정리 로직이 오동작
     */
    @Test
    @DisplayName("subscribeRoom과 revokeAllRoomSubscriptions 동시 실행 시 두 인덱스가 일관성을 유지한다")
    void subscribeRoom_revokeAll_동시_인덱스_일관성() throws Exception {
        int sessionCount = 20;
        long roomId = 9300L;
        String[] sids = new String[sessionCount];

        // Given
        for (int i = 0; i < sessionCount; i++) {
            sids[i] = "revoke-race-sid-" + i;
            WebSocketSession ws = mock(WebSocketSession.class);
            lenient().when(ws.isOpen()).thenReturn(true);
            registry.register(sids[i], ws, (long) (9300 + i), ROLE, FUTURE_EXPIRY, CONNECTED_AT);
        }

        int totalThreads = sessionCount + 1;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreads);

        // When
        for (int i = 0; i < sessionCount; i++) {
            final String sid = sids[i];
            final String subId = "sub-revoke-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    registry.subscribeRoom(sid, subId, roomId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                registry.revokeAllRoomSubscriptions(roomId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Then
        Set<String> roomMembers = getRoomSessions(roomId);
        for (String sid : sids) {
            boolean inRoomSessions = roomMembers != null && roomMembers.contains(sid);
            boolean inSubscribedRooms = registry.get(sid)
                    .map(info -> info.subscribedRooms().containsValue(roomId))
                    .orElse(false);
            assertThat(inRoomSessions)
                    .as("sid=%s: roomSessions와 subscribedRooms 불일치 (subscribeRoom 비원자성 레이스)", sid)
                    .isEqualTo(inSubscribedRooms);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Set<String> getRoomSessions(long roomId) throws Exception {
        Field field = WebSocketSessionRegistry.class.getDeclaredField("roomSessions");
        field.setAccessible(true);
        Map<Long, Set<String>> map = (Map<Long, Set<String>>) field.get(registry);
        return map.get(roomId);
    }

    private void setGraceDeadlineToPast(String sid) throws Exception {
        Field field = WebSocketSessionRegistry.class.getDeclaredField("sessions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, WebSocketSessionRegistry.SessionInfo> sessions =
                (Map<String, WebSocketSessionRegistry.SessionInfo>) field.get(registry);
        sessions.computeIfPresent(sid, (_, info) -> info.withGraceDeadline(Instant.now().minusSeconds(1)));
    }
}
