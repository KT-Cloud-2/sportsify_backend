package com.sportsify.chat.integration;

import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.application.message.service.ReadReceiptFlusher;
import com.sportsify.chat.config.ChatIntegrationTestFixture;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("읽음 처리 통합 테스트")
class ReadReceiptIntegrationTest {

    private static final Long MEMBER_ID = 5001L;
    private static final Long GAME_ROOM_MEMBER_ID = 5003L;
    private static final Long MONOTONIC_MEMBER_ID = 5004L;
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
        Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @AfterEach
    void cleanupDb() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("DIRECT 채팅방 읽음 상태 갱신")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void CAS_동시_요청_최댓값만_Redis에_저장된다() throws InterruptedException {
        long roomId = 5100L;
        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, roomId, MEMBER_ID);
        int threadCount = 50;
        long maxMessageId = (long) threadCount * 10;

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

        String stored = redisTemplate.opsForValue().get(key);
        assertThat(stored).isEqualTo(String.valueOf(maxMessageId));
    }

    @Test
    @DisplayName("GAME 채팅방 읽음 처리 미지원")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void GAME_채팅방_읽음_처리_Redis_미저장() {
        ChatRoomJpaEntity room = fixture.createRoom("GAME방", "GAME", "ACTIVE", GAME_ROOM_MEMBER_ID);
        MessageJpaEntity message = fixture.createMessage(room.getId(), GAME_ROOM_MEMBER_ID);
        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, room.getId(), GAME_ROOM_MEMBER_ID);

        messageService.read(room.getId(), GAME_ROOM_MEMBER_ID, message.getId(), true);

        String stored = redisTemplate.opsForValue().get(key);
        assertThat(stored).isNull();
    }

    @Test
    @DisplayName("flush 후 더 작은 messageId로 read 호출 시 Redis에는 저장되지만 DB는 갱신되지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 더_작은_messageId_flush_후_DB_갱신_안됨() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "DIRECT", "ACTIVE", 5005L);
        ChatRoomMemberJpaEntity member = fixture.createMember(room.getId(), MONOTONIC_MEMBER_ID, "JOINED");
        long smallMessageId = fixture.createMessage(room.getId(), MONOTONIC_MEMBER_ID).getId();
        long largeMessageId = fixture.createMessage(room.getId(), MONOTONIC_MEMBER_ID).getId();
        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, room.getId(), MONOTONIC_MEMBER_ID);


        messageService.read(room.getId(), MONOTONIC_MEMBER_ID, largeMessageId, true);
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(largeMessageId));


        readReceiptFlusher.flush();
        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(chatRoomMemberJpaRepo.findById(member.getId()).orElseThrow().getLastReadMessageId())
                .isEqualTo(largeMessageId);


        messageService.read(room.getId(), MONOTONIC_MEMBER_ID, smallMessageId, true);
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(smallMessageId));


        readReceiptFlusher.flush();
        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(chatRoomMemberJpaRepo.findById(member.getId()).orElseThrow().getLastReadMessageId())
                .isEqualTo(largeMessageId);
        verify(chatEventPublisher, times(1)).publishToRoom(anyLong(), any());
    }

    @Test
    @DisplayName("읽음 데이터 Redis-DB 동기화")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ReadReceiptFlusher_Redis_DB_동기화() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", 5002L);
        ChatRoomMemberJpaEntity member = fixture.createMember(room.getId(), MEMBER_ID, "JOINED");
        long messageId = fixture.createMessage(room.getId(), MEMBER_ID).getId();

        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, room.getId(), MEMBER_ID);
        redisTemplate.opsForValue().set(key, String.valueOf(messageId));

        readReceiptFlusher.flush();

        ChatRoomMemberJpaEntity updated = chatRoomMemberJpaRepo.findById(member.getId()).orElseThrow();
        assertThat(updated.getLastReadMessageId()).isEqualTo(messageId);
        verify(chatEventPublisher).publishToRoom(anyLong(), any());
    }

    @Test
    @DisplayName("JOINED 상태가 아닌 멤버의 읽음 데이터는 flush 시 DB에 반영되지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void BANNED_멤버_flush_DB_미반영() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "DIRECT", "ACTIVE", 5007L);
        ChatRoomMemberJpaEntity member = fixture.createMember(room.getId(), 5006L, "BANNED");
        String key = String.format("%s%d:%d", REDIS_KEY_PREFIX, room.getId(), 5006L);

        redisTemplate.opsForValue().set(key, "100");

        readReceiptFlusher.flush();

        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(chatRoomMemberJpaRepo.findById(member.getId()).orElseThrow().getLastReadMessageId())
                .isNull();
        verify(chatEventPublisher, never()).publishToRoom(anyLong(), any());
    }
}
