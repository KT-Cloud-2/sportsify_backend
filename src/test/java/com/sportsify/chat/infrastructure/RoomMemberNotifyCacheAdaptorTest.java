package com.sportsify.chat.infrastructure;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.infrastructure.cache.RedisKeySchema;
import com.sportsify.chat.infrastructure.cache.RoomMemberNotifyCacheAdaptor;
import com.sportsify.config.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("[통합] RoomMemberNotifyCacheAdaptor Redis 분기 테스트")
class RoomMemberNotifyCacheAdaptorTest {

    private static final Long ROOM_ID = 1L;

    @Autowired
    private RoomMemberNotifyCacheAdaptor adaptor;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(key(ROOM_ID));
        redisTemplate.delete(lockKey(ROOM_ID));
    }

    // ──────────────────────── getNotifiableMemberIds ────────────────────────

    @Test
    @DisplayName("키가 없으면 Optional.empty()를 반환한다")
    void getNotifiableMemberIds_키_없음_empty() {
        Optional<Set<Long>> result = adaptor.getNotifiableMemberIds(ROOM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("알림 활성 멤버만 필터링해 반환한다 (\"0\" 값은 제외)")
    void getNotifiableMemberIds_알림_비활성_멤버_제외() {
        adaptor.populate(ROOM_ID, List.of(
                member(10L, true),
                member(20L, false),
                member(30L, true)
        ));

        Optional<Set<Long>> result = adaptor.getNotifiableMemberIds(ROOM_ID);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrder(10L, 30L);
        assertThat(result.get()).doesNotContain(20L);
    }

    // ──────────────────────── populate ────────────────────────

    @Test
    @DisplayName("멤버 목록을 Redis에 알림 활성 여부와 함께 저장한다")
    void populate_성공() {
        adaptor.populate(ROOM_ID, List.of(
                member(10L, true),
                member(20L, false)
        ));

        assertThat(redisTemplate.hasKey(key(ROOM_ID))).isTrue();
        assertThat(redisTemplate.opsForHash().get(key(ROOM_ID), "10")).isEqualTo("1");
        assertThat(redisTemplate.opsForHash().get(key(ROOM_ID), "20")).isEqualTo("0");
    }

    @Test
    @DisplayName("빈 멤버 목록은 아무것도 저장하지 않는다")
    void populate_빈_목록_조기_반환() {
        adaptor.populate(ROOM_ID, List.of());

        assertThat(redisTemplate.hasKey(key(ROOM_ID))).isFalse();
    }

    @Test
    @DisplayName("락 획득 실패 시 populate는 데이터를 저장하지 않고 반환한다")
    void populate_락_획득_실패_조기_반환() {
        holdLock(ROOM_ID);

        adaptor.populate(ROOM_ID, List.of(member(10L, true)));

        assertThat(redisTemplate.hasKey(key(ROOM_ID))).isFalse();
    }

    // ──────────────────────── put ────────────────────────

    @Test
    @DisplayName("notificationEnabled=false이면 \"0\"으로 저장된다")
    void put_알림_비활성_0으로_저장() {
        adaptor.populate(ROOM_ID, List.of(member(10L, true)));

        adaptor.put(ROOM_ID, 10L, false);

        String value = (String) redisTemplate.opsForHash().get(key(ROOM_ID), "10");
        assertThat(value).isEqualTo("0");
    }

    @Test
    @DisplayName("락 획득 실패 시 put은 데이터를 수정하지 않고 반환한다")
    void put_락_획득_실패_조기_반환() {
        adaptor.populate(ROOM_ID, List.of(member(10L, false)));
        holdLock(ROOM_ID);

        adaptor.put(ROOM_ID, 10L, true);

        String value = (String) redisTemplate.opsForHash().get(key(ROOM_ID), "10");
        assertThat(value).isEqualTo("0");
    }

    // ──────────────────────── remove ────────────────────────

    @Test
    @DisplayName("존재하는 멤버를 remove하면 해당 항목이 삭제된다")
    void remove_존재하는_멤버_삭제() {
        adaptor.populate(ROOM_ID, List.of(member(10L, true), member(20L, true)));

        adaptor.remove(ROOM_ID, 10L);

        assertThat(redisTemplate.opsForHash().get(key(ROOM_ID), "10")).isNull();
        assertThat(redisTemplate.opsForHash().get(key(ROOM_ID), "20")).isEqualTo("1");
    }

    @Test
    @DisplayName("존재하지 않는 멤버를 remove해도 예외 없이 정상 처리된다")
    void remove_존재하지_않는_멤버_정상처리() {
        adaptor.populate(ROOM_ID, List.of(member(10L, true)));

        adaptor.remove(ROOM_ID, 99L);

        assertThat(redisTemplate.opsForHash().get(key(ROOM_ID), "10")).isEqualTo("1");
        assertThat(redisTemplate.opsForHash().get(key(ROOM_ID), "99")).isNull();
    }

    @Test
    @DisplayName("락 획득 실패 시 remove는 데이터를 삭제하지 않고 반환한다")
    void remove_락_획득_실패_조기_반환() {
        adaptor.populate(ROOM_ID, List.of(member(10L, true)));
        holdLock(ROOM_ID);

        adaptor.remove(ROOM_ID, 10L);

        String value = (String) redisTemplate.opsForHash().get(key(ROOM_ID), "10");
        assertThat(value).isEqualTo("1");
    }

    // ──────────────────────── evict ────────────────────────

    @Test
    @DisplayName("evict 호출 시 해당 방의 키가 삭제된다")
    void evict_키_삭제() {
        adaptor.populate(ROOM_ID, List.of(member(10L, true)));

        adaptor.evict(ROOM_ID);

        assertThat(redisTemplate.hasKey(key(ROOM_ID))).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 키를 evict해도 예외 없이 정상 처리된다")
    void evict_존재하지_않는_키_정상처리() {
        assertThat(redisTemplate.hasKey(key(ROOM_ID))).isFalse();

        adaptor.evict(ROOM_ID);

        assertThat(redisTemplate.hasKey(key(ROOM_ID))).isFalse();
    }

    // ──────────────────────── 헬퍼 ────────────────────────

    private void holdLock(Long roomId) {
        redisTemplate.opsForValue().set(lockKey(roomId), "external-owner",
                Duration.ofSeconds(10));
    }

    private ChatRoomMember member(Long memberId, boolean notificationEnabled) {
        ChatRoomMember m = mock(ChatRoomMember.class);
        com.sportsify.chat.domain.model.chatRoom.MemberId id =
                com.sportsify.chat.domain.model.chatRoom.MemberId.of(memberId);
        when(m.getMemberId()).thenReturn(id);
        when(m.isNotificationEnabled()).thenReturn(notificationEnabled);
        return m;
    }

    private String key(Long roomId) {
        return String.format(RedisKeySchema.ROOM_NOTIFY_KEY_PREFIX, roomId);
    }

    private String lockKey(Long roomId) {
        return String.format(RedisKeySchema.ROOM_NOTIFY_LOCK_KEY_PREFIX, roomId);
    }
}
