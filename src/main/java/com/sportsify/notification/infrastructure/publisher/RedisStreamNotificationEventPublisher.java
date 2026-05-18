package com.sportsify.notification.infrastructure.publisher;

import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamNotificationEventPublisher implements NotificationEventPublisher {

    public static final String PAYLOAD_KEY = "payload";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(NotificationEventType eventType, NotificationPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForStream().add(eventType.getStreamKey(), Map.of(PAYLOAD_KEY, json));
            log.info("알림 이벤트 발행 stream={}", eventType.getStreamKey());
        } catch (Exception e) {
            log.warn("알림 이벤트 발행 실패 eventType={}", eventType, e);
            throw new IllegalArgumentException("알림 payload 직렬화 실패 eventType=" + eventType, e);
        }
    }
}
