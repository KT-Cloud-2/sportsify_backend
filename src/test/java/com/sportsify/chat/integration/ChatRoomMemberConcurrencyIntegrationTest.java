package com.sportsify.chat.integration;

import com.sportsify.chat.application.chatRoom.dto.CreateChatRoomRequest;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.support.RepositoryTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [통합] 채팅방 멤버 동시성 충돌 통합 테스트
 * <p>
 * 테스트 가치: 동시성 충돌 제어
 * - PostgreSQL Advisory Lock이 실제 동시 요청에서 정상 작동하는지 검증
 * - 대규모 트래픽 환경에서 중복 DIRECT 방 생성과 중복 멤버 입장을 방지할 수 있는지 검증
 * <p>
 * 단위 테스트와의 차이:
 * - 단위 테스트는 pg_try_advisory_xact_lock Mock으로 호출 여부만 검증
 * - 통합 테스트는 실제 PostgreSQL에서 멀티스레드 경쟁 상황의 결과를 검증
 */
@DisplayName("[통합] 채팅방 멤버 동시성 충돌 통합 테스트")
class ChatRoomMemberConcurrencyIntegrationTest extends RepositoryTestSupport {

    private static final int THREAD_COUNT = 50;
    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomMemberService chatRoomMemberService;
    @Autowired
    private ChatIntegrationTestFixture fixture;

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - pg_try_advisory_xact_lock은 PostgreSQL 전용 기능으로 실제 DB 연결이 필요
     * - 동시에 두 요청이 existByCreatorIdAndInviteId() 조회를 통과하면 중복 방이 생성되는 버그를 락으로 방지
     * <p>
     * 실패 가능 포인트:
     * - Advisory Lock 없이 50개 스레드가 동시에 중복 체크를 통과하면 최대 50개의 DM 방이 생성됨
     * - 락 획득 후 커밋 사이에 다른 스레드가 체크를 통과하면 2개 이상의 방이 생성됨
     */
    @Test
    @DisplayName("동일 사용자 쌍의 DIRECT 방 동시 생성 시 하나만 성공한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void DIRECT_방_동시_생성_하나만_성공() throws InterruptedException {
        long creatorId = 2001L;
        long inviteeId = 2002L;
        fixture.createMemberRecord(creatorId);
        fixture.createMemberRecord(inviteeId);
        CreateChatRoomRequest request =
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(inviteeId));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    chatRoomService.create(request, creatorId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - 1);
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - join()은 Advisory Lock과 (room_id, member_id) 유니크 제약 두 가지로 동시성을 방어
     * - 락 없이 동시 진입하면 DataIntegrityViolationException 발생 또는 JOINED 멤버 중복 생성 우려
     * <p>
     * 실패 가능 포인트:
     * - Advisory Lock이 동작하지 않으면 saveAndFlush() 시점에 유니크 제약 위반 예외가 전파
     * - 서비스가 DataIntegrityViolationException → BusinessException 변환을 누락하면 500 에러 발생
     */
    @Test
    @DisplayName("같은 방에 같은 멤버가 동시에 입장 시 하나만 성공한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동일_멤버_동시_입장_하나만_성공() throws InterruptedException {
        ChatRoomJpaEntity room = fixture.createRoom("동시성테스트방", "GAME", "ACTIVE", 3001L);
        long memberId = 3002L;
        fixture.createMemberRecord(memberId);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    chatRoomMemberService.join(room.getId(), memberId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - 1);
    }
}
