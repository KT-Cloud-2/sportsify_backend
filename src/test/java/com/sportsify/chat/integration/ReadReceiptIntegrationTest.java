package com.sportsify.chat.integration;

import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.application.message.service.ReadReceiptFlusher;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.config.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [통합] Redis 읽음 처리 인프라 연동 통합 테스트
 * <p>
 * 테스트 가치: 인프라 연동 (Redis ↔ DB)
 * - Redis CAS 스크립트의 원자적 업데이트가 실제 Redis에서 동작하는지 검증
 * - ReadReceiptFlusher의 Redis→DB 동기화 전체 흐름을 검증
 * <p>
 * 단위 테스트와의 차이:
 * - 단위 테스트는 Redis를 Mock으로 대체해 CAS 스크립트 결과를 임의로 주입
 * - 통합 테스트는 실제 Redis에서 Lua 스크립트 원자성과 DB 반영까지 End-to-End 검증
 *
 * @MockitoBean ChatEventPublisher 이유:
 * - flush() 완료 후 WebSocket 이벤트를 발행하는데, 이 통합 테스트는 Redis→DB 흐름이 목적
 * - WebSocket 연결 없이도 SimpMessagingTemplate은 동작하지만,
 * ChatEventPublisher를 Mock으로 대체해 테스트 범위를 인프라 연동으로 명확히 제한
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("[통합] Redis 읽음 처리 인프라 연동 통합 테스트")
class ReadReceiptIntegrationTest {

    private static final Long MEMBER_ID = 5001L;
    private static final String REDIS_KEY_PREFIX = "chat:read:";
    @Autowired
    private MessageService messageService;
    @Autowired
    private ReadReceiptFlusher readReceiptFlusher;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepo;
    @Autowired
    private ChatIntegrationTestFixture fixture;
    @MockitoBean
    private ChatEventPublisher chatEventPublisher;

    @AfterEach
    void cleanupRedis() {
        Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "5*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @AfterEach
    void cleanupDb() {
        fixture.deleteAll();
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - Lua CAS 스크립트의 원자성은 실제 Redis + 동시 요청 환경에서만 의미 있게 검증 가능
     * - 단위 테스트는 execute() 반환값을 Mock하므로 스크립트 자체의 동작을 검증하지 않음
     * - 순차 호출로는 단순 GET-SET 구현과 결과가 같아 CAS 원자성 검증이 되지 않음
     * <p>
     * 실패 가능 포인트:
     * - CAS 없이 단순 SET이면 마지막으로 실행된 스레드 값이 저장돼 최댓값 보장 불가
     * - Lua 스크립트 tonumber() 변환 실패 시 모든 SET이 통과되거나 모두 거부될 수 있음
     * - Redis 직렬화 설정이 스크립트에 영향을 줘 숫자 비교가 문자열 비교로 오동작할 수 있음
     */
    @Test
    @DisplayName("여러 스레드가 동시에 읽음 처리 시 최댓값만 Redis에 저장된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void CAS_동시_요청_최댓값만_Redis에_저장된다() throws InterruptedException {
        // Given
        long roomId = 5100L;
        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, roomId, MEMBER_ID);
        int threadCount = 50;
        long maxMessageId = (long) threadCount * 10;  // 500

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 1; i <= threadCount; i++) {
            final long messageId = (long) i * 10;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    messageService.read(roomId, MEMBER_ID, messageId, false);
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

        // Then
        String stored = redisTemplate.opsForValue().get(key);
        assertThat(stored).isEqualTo(String.valueOf(maxMessageId));
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - flush()는 Redis SCAN → getAndDelete → TransactionTemplate(DB 업데이트) 흐름 전체가 연계됨
     * - 각 컴포넌트를 단위 테스트로 검증해도 통합 실행 흐름의 버그는 놓칠 수 있음
     * <p>
     * 실패 가능 포인트:
     * - Redis key 파싱(chat:read:{roomId}:{memberId}) 오류 시 DB 업데이트가 건너뛰어짐
     * - TransactionTemplate 설정 문제로 updateLastReadMessageIfGreater가 롤백되면 DB 미반영
     * - getAndDelete() 후 flush 전 장애 발생 시 Redis 데이터는 유실, DB는 미반영 상태 됨
     */
    @Test
    @DisplayName("ReadReceiptFlusher 실행 시 Redis의 읽음 데이터가 DB에 반영된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ReadReceiptFlusher_Redis_DB_동기화() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", 5002L);
        ChatRoomMemberJpaEntity member = fixture.createMember(room.getId(), MEMBER_ID, "JOINED");
        // chat_room_members.last_read_message_id → chat_messages.id FK 충족을 위해 실제 메시지 생성
        long messageId = fixture.createMessage(room.getId(), MEMBER_ID).getId();

        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, room.getId(), MEMBER_ID);
        redisTemplate.opsForValue().set(key, String.valueOf(messageId));

        // When
        readReceiptFlusher.flush();

        // Then
        ChatRoomMemberJpaEntity updated = chatRoomMemberJpaRepo.findById(member.getId()).orElseThrow();
        assertThat(updated.getLastReadMessageId()).isEqualTo(messageId);
    }
}
