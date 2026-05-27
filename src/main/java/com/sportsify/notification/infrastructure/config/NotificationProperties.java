package com.sportsify.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Retry retry,
        Pel pel,
        Stream stream,
        Scheduler scheduler,
        Slack slack
) {
    public record Retry(int maxRetry) {}

    public record Pel(Duration claimMinIdle, int batchSize, Duration stuckTimeout, List<Integer> backoffMinutes) {}

    public record Stream(int maxLen) {}

    public record Scheduler(String reservedDispatchCron, String streamTrimCron, String stuckRecoveryCron, String pelReclaimCron, Duration leaderLockTtl) {}

    public record Slack(String webhookUrl, String signingSecret, Duration suppressTtl) {}
}
