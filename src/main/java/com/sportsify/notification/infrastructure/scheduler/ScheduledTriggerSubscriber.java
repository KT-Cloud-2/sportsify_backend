package com.sportsify.notification.infrastructure.scheduler;

import com.sportsify.notification.application.service.ScheduledEventClaimService;
import com.sportsify.notification.application.service.ScheduledNotificationProcessor;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisPubSubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTriggerSubscriber extends MessageListenerAdapter {

    private final ScheduledNotificationProcessor scheduledProcessor;
    private final ScheduledEventClaimService claimService;
    private final NotificationProperties properties;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());

        if (RedisPubSubConfig.SCHEDULED_TRIGGER_CHANNEL.equals(channel)) {
            log.info("예약 알림 트리거 수신");
            scheduledProcessor.processDue();
            return;
        }

        if (RedisPubSubConfig.STUCK_RECOVERY_CHANNEL.equals(channel)) {
            log.info("stuck 복구 트리거 수신");
            LocalDateTime stuckBefore = LocalDateTime.now().minus(properties.pel().stuckTimeout());
            claimService.claimStuckEvents(stuckBefore);
        }
    }
}
