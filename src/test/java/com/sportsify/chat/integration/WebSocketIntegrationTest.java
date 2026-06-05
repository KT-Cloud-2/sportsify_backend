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

@DisplayName("WebSocket 통합 테스트")
class WebSocketIntegrationTest {

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

    @Test
    @DisplayName("동시 구독 인덱스 일관성")
    void 동시_방_구독_인덱스_일관성() throws Exception {
        int threadCount = 30;
        long roomId = 9001L;
        String[] sids = new String[threadCount];

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

    @Test
    @DisplayName("JWT 토큰 만료 및 Grace Period")
    void evictExpiredSessions_updateExpiry_동시_실행_세션_유지() throws Exception {
        String sid = "expiry-race-sid";
        WebSocketSession ws = mock(WebSocketSession.class);
        lenient().when(ws.isOpen()).thenReturn(true);
        registry.register(sid, ws, 9100L, ROLE, Instant.now().minusSeconds(1), CONNECTED_AT);
        registry.evictExpiredSessions();
        setGraceDeadlineToPast(sid);

        Instant newExpiry = Instant.now().plusSeconds(7200);
        int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

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

        registry.updateExpiry(sid, newExpiry);
        reset(ws);
        lenient().when(ws.isOpen()).thenReturn(true);
        registry.evictExpiredSessions();
        verify(ws, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("동시 강제 연결 해제 안전성")
    void forceDisconnect_동시_다중_호출_안전성() throws Exception {
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

        assertThat(exceptionCount.get()).as("예외가 스레드 밖으로 전파되면 안 된다").isZero();
        assertThat(registry.get(sid)).isEmpty();
        Set<String> roomMembers = getRoomSessions(9200L);
        assertThat(roomMembers == null || !roomMembers.contains(sid))
                .as("roomSessions 인덱스도 정리되어야 한다").isTrue();
    }

    @Test
    @DisplayName("BAN 처리 직후 WebSocket 구독 자동 해제")
    void subscribeRoom_revokeAll_동시_인덱스_일관성() throws Exception {
        int sessionCount = 20;
        long roomId = 9300L;
        String[] sids = new String[sessionCount];

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
