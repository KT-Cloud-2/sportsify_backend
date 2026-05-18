package com.sportsify.notification.infrastructure.consumer;

import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamConsumer {

    private static final Map<String, NotificationEventType> STREAM_TO_EVENT =
            NotificationEventType.streamKeyMap();

    private final StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private final NotificationEventProcessor processor;

    @Value("${spring.application.name:app}-consumer-${HOSTNAME:local}")
    private String consumerName;

    @PostConstruct
    public void registerListeners() {
        for (Map.Entry<String, NotificationEventType> entry : STREAM_TO_EVENT.entrySet()) {
            String streamKey = entry.getKey();
            NotificationEventType eventType = entry.getValue();

            container.receive(
                    Consumer.from(RedisStreamsConfig.NOTIFICATION_GROUP, consumerName),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    message -> handleMessage(streamKey, eventType, message)
            );
        }
    }

    private void handleMessage(String streamKey, NotificationEventType eventType, ObjectRecord<String, String> message) {
        try {
            processor.process(eventType, message.getValue());
            // 예약 이벤트도 ACK — DB에 PENDING 상태로 저장됐으므로 Stream 재처리 불필요
            redisTemplate.opsForStream().acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId());
            log.info("Stream ACK streamKey={} id={}", streamKey, message.getId());
        } catch (Exception e) {
            log.error("Stream processing failed streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
        }
    }
}
