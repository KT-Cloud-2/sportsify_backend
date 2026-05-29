package com.sportsify.notification.application.listener;

import com.sportsify.common.event.NotificationPermanentlyFailedEvent;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import com.sportsify.notification.infrastructure.slack.SlackNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FailureEventListener {

    private static final String SUPPRESS_KEY_PREFIX = "notification:slack:suppress:";

    private final SlackNotifier slackNotifier;
    private final NotificationProperties properties;
    private final StringRedisTemplate redisTemplate;

    public FailureEventListener(SlackNotifier slackNotifier,
                                            NotificationProperties properties,
                                            StringRedisTemplate redisTemplate) {
        this.slackNotifier = slackNotifier;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Async
    @EventListener
    public void onPermanentlyFailed(NotificationPermanentlyFailedEvent event) {
        log.error("[알림 영구 실패] eventId={} eventType={} retryCount={} source={}",
                event.eventId(), event.eventType(), event.retryCount(), event.source());

        if (!isSlackEnabled() || isSuppressed(event)) {
            return;
        }
        slackNotifier.send(buildMessage(event));
    }

    private boolean isSlackEnabled() {
        String url = properties.slack().webhookUrl();
        return url != null && !url.isBlank();
    }

    private boolean isSuppressed(NotificationPermanentlyFailedEvent event) {
        // eventType 단위로 suppress — 같은 타입의 연속 실패는 첫 알림만 전송
        String key = SUPPRESS_KEY_PREFIX + event.eventType();
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", properties.slack().suppressTtl());
        return !Boolean.TRUE.equals(isNew);
    }

    private String buildMessage(NotificationPermanentlyFailedEvent event) {
        return String.format(
                "알림 영구 실패\neventId: %d\neventType: %s\nretryCount: %d\nsource: %s\noccurredAt: %s",
                event.eventId(), event.eventType(), event.retryCount(), event.source(), event.occurredAt()
        );
    }
}
