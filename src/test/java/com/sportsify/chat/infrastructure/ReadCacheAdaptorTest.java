package com.sportsify.chat.infrastructure;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.ReadCache;
import com.sportsify.chat.infrastructure.cache.ReadCacheAdaptor;
import com.sportsify.chat.infrastructure.cache.RedisKeySchema;
import com.sportsify.config.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("[통합] ReadCacheAdaptor CAS 동작 테스트")
class ReadCacheAdaptorTest {

    private static final Long ROOM_ID = 9001L;
    private static final Long MEMBER_ID = 9002L;

    @Autowired
    private ReadCacheAdaptor adaptor;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("chat:read:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void put(Long roomId, Long memberId, Long messageId) {
        adaptor.put(ChatRoomId.of(roomId), MemberId.of(memberId), MessageId.of(messageId));
    }

    private String key(Long roomId, Long memberId) {
        return String.format(RedisKeySchema.LAST_READ_KEY_PREFIX, roomId, memberId);
    }

    // ──────────────────────── put ────────────────────────

    @Test
    @DisplayName("키가 없을 때 첫 번째 값이 저장된다")
    void put_키없음_첫값_저장() {
        put(ROOM_ID, MEMBER_ID, 100L);

        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, MEMBER_ID))).isEqualTo("100");
    }

    @Test
    @DisplayName("기존 값보다 큰 값이 들어오면 덮어쓴다")
    void put_더큰값_덮어쓴다() {
        put(ROOM_ID, MEMBER_ID, 100L);
        put(ROOM_ID, MEMBER_ID, 200L);

        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, MEMBER_ID))).isEqualTo("200");
    }

    @Test
    @DisplayName("기존 값보다 작은 값이 들어오면 기존 값을 유지한다")
    void put_더작은값_기존값_유지() {
        put(ROOM_ID, MEMBER_ID, 500L);
        put(ROOM_ID, MEMBER_ID, 100L);

        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, MEMBER_ID))).isEqualTo("500");
    }

    @Test
    @DisplayName("기존 값과 동일한 값이 들어오면 기존 값을 유지한다 (등호 미허용)")
    void put_동일한값_기존값_유지() {
        put(ROOM_ID, MEMBER_ID, 300L);
        put(ROOM_ID, MEMBER_ID, 300L);

        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, MEMBER_ID))).isEqualTo("300");
    }

    @Test
    @DisplayName("messageId 최솟값(1)이 정상 저장된다")
    void put_최솟값_저장() {
        put(ROOM_ID, MEMBER_ID, 1L);

        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, MEMBER_ID))).isEqualTo("1");
    }

    @Test
    @DisplayName("저장 후 TTL이 설정된다")
    void put_TTL_설정() {
        put(ROOM_ID, MEMBER_ID, 100L);

        Long ttl = redisTemplate.getExpire(key(ROOM_ID, MEMBER_ID), TimeUnit.SECONDS);
        assertThat(ttl).isPositive();
    }

    @Test
    @DisplayName("다른 roomId와 다른 memberId는 서로 독립적으로 저장된다")
    void put_다른_roomId_다른_memberId_독립저장() {
        put(9001L, 9002L, 100L);
        put(9003L, 9004L, 200L);

        assertThat(redisTemplate.opsForValue().get(key(9001L, 9002L))).isEqualTo("100");
        assertThat(redisTemplate.opsForValue().get(key(9003L, 9004L))).isEqualTo("200");
    }

    @Test
    @DisplayName("같은 roomId에 다른 memberId는 서로 독립적으로 저장된다")
    void put_같은_roomId_다른_memberId_독립저장() {
        put(ROOM_ID, 9002L, 100L);
        put(ROOM_ID, 9005L, 200L);

        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, 9002L))).isEqualTo("100");
        assertThat(redisTemplate.opsForValue().get(key(ROOM_ID, 9005L))).isEqualTo("200");
    }

    // ──────────────────────── drainAll ────────────────────────

    @Test
    @DisplayName("저장된 항목을 반환하고 키를 삭제한다")
    void drainAll_항목반환_후_키삭제() {
        put(ROOM_ID, MEMBER_ID, 100L);

        List<ReadCache.ReadEntry> entries = adaptor.drainAll();

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().roomId()).isEqualTo(ChatRoomId.of(ROOM_ID));
        assertThat(entries.getFirst().memberId()).isEqualTo(MemberId.of(MEMBER_ID));
        assertThat(entries.getFirst().messageId()).isEqualTo(MessageId.of(100L));
        assertThat(redisTemplate.hasKey(key(ROOM_ID, MEMBER_ID))).isFalse();
    }

    @Test
    @DisplayName("키가 없으면 빈 목록을 반환한다")
    void drainAll_키없음_빈목록() {
        List<ReadCache.ReadEntry> entries = adaptor.drainAll();

        assertThat(entries).isEmpty();
    }

    @Test
    @DisplayName("roomId가 0인 잘못된 키는 건너뛰고 유효한 항목만 반환한다")
    void drainAll_잘못된_roomId_BusinessException_건너뜀() {
        redisTemplate.opsForValue().set("chat:read:0:9002", "100");
        put(ROOM_ID, MEMBER_ID, 200L);

        List<ReadCache.ReadEntry> entries = adaptor.drainAll();

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().messageId()).isEqualTo(MessageId.of(200L));
    }

    @Test
    @DisplayName("여러 방/멤버의 항목을 모두 반환하고 키를 삭제한다")
    void drainAll_복수항목_모두반환_후_키삭제() {
        put(9001L, 9002L, 100L);
        put(9003L, 9004L, 200L);

        List<ReadCache.ReadEntry> entries = adaptor.drainAll();

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(e -> e.messageId().value())
                .containsExactlyInAnyOrder(100L, 200L);
        assertThat(redisTemplate.hasKey(key(9001L, 9002L))).isFalse();
        assertThat(redisTemplate.hasKey(key(9003L, 9004L))).isFalse();
    }
}
