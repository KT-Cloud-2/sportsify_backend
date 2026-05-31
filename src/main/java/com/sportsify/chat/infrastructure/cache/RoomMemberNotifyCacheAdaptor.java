package com.sportsify.chat.infrastructure.cache;

import com.sportsify.chat.domain.model.chatRoomMember.ChatRoomMember;
import com.sportsify.chat.domain.repository.RoomMemberNotifyCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomMemberNotifyCacheAdaptor implements RoomMemberNotifyCache {

    private static final int LOCK_MAX_RETRIES = 3;
    private static final long LOCK_RETRY_DELAY_MS = 10L;


    private static final DefaultRedisScript<Long> CONDITIONAL_HSET_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
                        redis.call('EXPIRE', KEYS[1], ARGV[3])
                        return 1
                    end
                    return 0
                    """,
            Long.class
    );

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """,
            Long.class
    );

    private static final ThreadLocal<String> LOCK_OWNER = new ThreadLocal<>();

    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(Long roomId, Long memberId, boolean notificationEnabled) {
        String lockKey = lockKey(roomId);
        if (!tryAcquireLock(lockKey)) {
            log.warn("put 락 획득 실패 roomId={} memberId={}", roomId, memberId);
            return;
        }
        try {
            redisTemplate.execute(
                    CONDITIONAL_HSET_SCRIPT,
                    List.of(key(roomId)),
                    String.valueOf(memberId),
                    notificationEnabled ? "1" : "0",
                    String.valueOf(RedisKeySchema.ROOM_NOTIFY_TTL_SECONDS)
            );
        } finally {
            releaseLock(lockKey);
        }
    }

    @Override
    public void remove(Long roomId, Long memberId) {
        String lockKey = lockKey(roomId);
        if (!tryAcquireLock(lockKey)) {
            log.warn("remove 락 획득 실패 roomId={} memberId={}", roomId, memberId);
            return;
        }
        try {
            redisTemplate.opsForHash().delete(key(roomId), String.valueOf(memberId));
        } finally {
            releaseLock(lockKey);
        }
    }

    @Override
    public void evict(Long roomId) {
        redisTemplate.delete(key(roomId));
    }

    @Override
    public Optional<Set<Long>> getNotifiableMemberIds(Long roomId) {
        String key = key(roomId);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            return Optional.empty();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Set<Long> notifiable = entries.entrySet().stream()
                .filter(e -> "1".equals(e.getValue()))
                .map(e -> Long.parseLong((String) e.getKey()))
                .collect(Collectors.toSet());
        return Optional.of(notifiable);
    }

    @Override
    public void populate(Long roomId, List<ChatRoomMember> members) {
        if (members.isEmpty()) return;
        String lockKey = lockKey(roomId);
        if (!tryAcquireLock(lockKey)) {
            log.warn("populate 락 획득 실패 roomId={}", roomId);
            return;
        }
        try {
            String key = key(roomId);
            Map<String, String> map = members.stream().collect(Collectors.toMap(
                    m -> String.valueOf(m.getMemberId().value()),
                    m -> m.isNotificationEnabled() ? "1" : "0"
            ));
            redisTemplate.opsForHash().putAll(key, map);
            redisTemplate.expire(key, Duration.ofSeconds(RedisKeySchema.ROOM_NOTIFY_TTL_SECONDS));
        } finally {
            releaseLock(lockKey);
        }
    }

    private boolean tryAcquireLock(String lockKey) {
        String owner = UUID.randomUUID().toString();
        for (int i = 0; i < LOCK_MAX_RETRIES; i++) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, owner, Duration.ofMillis(RedisKeySchema.ROOM_NOTIFY_LOCK_TTL_MS));
            if (Boolean.TRUE.equals(acquired)) {
                LOCK_OWNER.set(owner);
                return true;
            }
            try {
                Thread.sleep(LOCK_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void releaseLock(String lockKey) {
        String owner = LOCK_OWNER.get();
        if (owner != null) {
            try {
                redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), owner);
            } finally {
                LOCK_OWNER.remove();
            }
        }
    }

    private String key(Long roomId) {
        return String.format(RedisKeySchema.ROOM_NOTIFY_KEY_PREFIX, roomId);
    }

    private String lockKey(Long roomId) {
        return String.format(RedisKeySchema.ROOM_NOTIFY_LOCK_KEY_PREFIX, roomId);
    }
}
