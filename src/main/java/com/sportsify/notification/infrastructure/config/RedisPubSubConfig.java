package com.sportsify.notification.infrastructure.config;

import com.sportsify.notification.infrastructure.scheduler.ScheduledTriggerSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    public static final String SCHEDULED_TRIGGER_CHANNEL = "notification:scheduled:trigger";
    public static final String STUCK_RECOVERY_CHANNEL = "notification:stuck:trigger";

    @Bean
    public ChannelTopic scheduledTriggerTopic() {
        return new ChannelTopic(SCHEDULED_TRIGGER_CHANNEL);
    }

    @Bean
    public ChannelTopic stuckRecoveryTopic() {
        return new ChannelTopic(STUCK_RECOVERY_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ScheduledTriggerSubscriber scheduledTriggerSubscriber,
            ChannelTopic scheduledTriggerTopic,
            ChannelTopic stuckRecoveryTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(scheduledTriggerSubscriber, scheduledTriggerTopic);
        container.addMessageListener(scheduledTriggerSubscriber, stuckRecoveryTopic);
        return container;
    }
}
