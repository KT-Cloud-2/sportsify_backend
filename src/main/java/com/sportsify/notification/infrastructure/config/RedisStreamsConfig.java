package com.sportsify.notification.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
public class RedisStreamsConfig {

    public static final String NOTIFICATION_GROUP = "notification-group";
    static final List<String> STREAM_KEYS = List.of(
            "ticket.opened", "payment.completed", "game.starting", "chat.mentioned"
    );

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
                log.debug("Consumer group already exists for stream={}", streamKey);
            }
        }
    }
}
