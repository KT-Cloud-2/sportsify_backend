package com.sportsify.notification.infrastructure.config;

import com.sportsify.notification.domain.model.NotificationEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class RedisStreamsConfig {

    public static final String NOTIFICATION_GROUP = "notification-group";
    public static final List<String> STREAM_KEYS = Arrays.stream(NotificationEventType.values())
            .map(NotificationEventType::getStreamKey)
            .toList();

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate
    ) {
        initConsumerGroups(redisTemplate);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofMillis(100))
                .targetType(String.class)
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
        return container;
    }

    private void initConsumerGroups(StringRedisTemplate redisTemplate) {
        for (String streamKey : STREAM_KEYS) {
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), NOTIFICATION_GROUP);
            } catch (Exception e) {
                if (isBusyGroup(e)) {
                    log.debug("Consumer group already exists stream={}", streamKey);
                    continue;
                }
                log.error("Consumer group 생성 실패 stream={} error={}", streamKey, e.getMessage(), e);
                throw new IllegalStateException("Redis Streams consumer group 초기화 실패: " + streamKey, e);
            }
        }
    }

    private boolean isBusyGroup(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
