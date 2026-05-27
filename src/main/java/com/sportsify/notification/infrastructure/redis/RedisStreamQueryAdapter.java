package com.sportsify.notification.infrastructure.redis;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.port.NotificationStreamQueryPort;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisStreamQueryAdapter implements NotificationStreamQueryPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> findPayload(NotificationEventType eventType, String messageId) {
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .range(eventType.getStreamKey(), Range.closed(messageId, messageId));
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        Object payload = messages.get(0).getValue().get(RedisStreamNotificationEventPublisher.PAYLOAD_KEY);
        return Optional.ofNullable(payload).map(String::valueOf);
    }
}
