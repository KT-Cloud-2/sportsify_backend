package com.sportsify.notification.application.consumer;

import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.domain.model.NotificationEventType;
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

    private static final String GROUP = "notification-group";
    private static final Map<String, NotificationEventType> STREAM_TO_EVENT = Map.of(
            "ticket.opened", NotificationEventType.TICKET_OPEN,
            "payment.completed", NotificationEventType.PAYMENT_COMPLETED,
            "game.starting", NotificationEventType.GAME_START,
            "chat.mentioned", NotificationEventType.CHAT_MENTION
    );

    private final StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private final NotificationEventProcessor processor;

    @Value("${spring.application.name:app}-consumer")
    private String consumerName;

    @PostConstruct
    public void registerListeners() {
        for (Map.Entry<String, NotificationEventType> entry : STREAM_TO_EVENT.entrySet()) {
            String streamKey = entry.getKey();
            NotificationEventType eventType = entry.getValue();

            container.receive(
                    Consumer.from(GROUP, consumerName),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    message -> handleMessage(streamKey, eventType, message)
            );
        }
    }

    private void handleMessage(String streamKey, NotificationEventType eventType, ObjectRecord<String, String> message) {
        try {
            processor.process(eventType, message.getValue());
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, message.getId());
            log.info("Stream ACK streamKey={} id={}", streamKey, message.getId());
        } catch (Exception e) {
            log.error("Stream processing failed streamKey={} id={} error={}", streamKey, message.getId(), e.getMessage());
        }
    }
}
