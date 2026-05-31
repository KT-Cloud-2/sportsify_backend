package com.sportsify.chat.infrastructure.cache;

import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;
import com.sportsify.chat.domain.model.message.MessageId;
import com.sportsify.chat.domain.repository.ReadCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadCacheAdaptor implements ReadCache {

    private static final DefaultRedisScript<Long> CAS_SCRIPT =
            new DefaultRedisScript<>(
                    """
                            local c = redis.call('GET', KEYS[1])
                            
                            if c == false or tonumber(ARGV[1]) > tonumber(c) then
                                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                                return 1
                            end
                            
                            return 0
                            """,
                    Long.class
            );
    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(ChatRoomId roomId, MemberId memberId, MessageId lastReadMessageId) {
        String key = String.format(RedisKeySchema.LAST_READ_KEY_PREFIX, roomId.value(), memberId.value());
        redisTemplate.execute(CAS_SCRIPT, List.of(key),
                String.valueOf(lastReadMessageId.value()), String.valueOf(RedisKeySchema.LAST_READ_TTL_SECONDS));
    }

    @Override
    public List<ReadCache.ReadEntry> drainAll() {
        List<ReadCache.ReadEntry> entries = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(RedisKeySchema.SCAN_PATTERN).count(200).build())) {
            cursor.forEachRemaining(key -> {
                String raw = redisTemplate.opsForValue().getAndDelete(key);
                if (raw == null) return;
                String[] parts = key.split(":");
                if (parts.length != 4) return;
                try {
                    entries.add(new ReadCache.ReadEntry(
                            ChatRoomId.of(Long.parseLong(parts[2])),
                            MemberId.of(Long.parseLong(parts[3])),
                            MessageId.of(Long.parseLong(raw))
                    ));
                } catch (NumberFormatException e) {
                    log.warn("Skipping malformed read receipt: key={}", key, e);
                }
            });
        }
        return entries;
    }


}
