package com.sportsify.notification.infrastructure.scheduler;

import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.config.RedisPubSubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTriggerPublisher {

    private static final String LEADER_LOCK_KEY = "notification:scheduler:leader";

    private final StringRedisTemplate redisTemplate;
    private final NotificationProperties properties;

    @Scheduled(cron = "${notification.scheduler.reserved-dispatch-cron}")
    public void triggerScheduled() {
        if (!acquireLeaderLock()) {
            return;
        }
        log.info("예약 알림 트리거 발행");
        redisTemplate.convertAndSend(RedisPubSubConfig.SCHEDULED_TRIGGER_CHANNEL, "trigger");
    }

    @Scheduled(cron = "${notification.scheduler.stuck-recovery-cron}")
    public void triggerStuckRecovery() {
        if (!acquireLeaderLock()) {
            return;
        }
        log.info("stuck 복구 트리거 발행");
        redisTemplate.convertAndSend(RedisPubSubConfig.STUCK_RECOVERY_CHANNEL, "trigger");
    }

    private boolean acquireLeaderLock() {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LEADER_LOCK_KEY, "1", properties.scheduler().leaderLockTtl());
        return Boolean.TRUE.equals(acquired);
    }
}
