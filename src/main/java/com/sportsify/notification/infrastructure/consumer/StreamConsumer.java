package com.sportsify.notification.infrastructure.consumer;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.application.service.EventStatusService;
import com.sportsify.notification.application.service.FanoutService;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.infrastructure.config.RedisStreamsConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamConsumer {

    private static final Map<String, NotificationEventType> STREAM_TO_EVENT =
            NotificationEventType.streamKeyMap();

    private final StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private final EventStatusService statusService;
    private final FanoutService fanoutService;

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
            NotificationEvent event = statusService.saveEventWithStreamMessageId(eventType, message.getValue(), message.getId().getValue());

            if (event.isScheduled()) {
                acknowledge(streamKey, message);
                log.info("예약 알림 저장 완료, ACK streamKey={} id={}", streamKey, message.getId());
                return;
            }

            boolean failed = fanoutService.fanout(event, eventType, message.getValue());
            statusService.markEventStatus(event.getId(), failed);
            if (failed) {
                log.warn("즉시 발송 실패, PEL 보류 streamKey={} id={}", streamKey, message.getId());
                return;
            }

            acknowledge(streamKey, message);
            log.info("즉시 발송 완료, ACK streamKey={} id={}", streamKey, message.getId());
        } catch (Exception e) {
            log.error("Stream 처리 실패 streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
        }
    }

    private void acknowledge(String streamKey, ObjectRecord<String, String> message) {
        redisTemplate.opsForStream().acknowledge(streamKey, RedisStreamsConfig.NOTIFICATION_GROUP, message.getId());
    }
}
