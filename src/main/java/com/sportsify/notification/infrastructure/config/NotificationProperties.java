package com.sportsify.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Pel pel,
        Stream stream,
        Scheduler scheduler,
        Channel channel,
        Fanout fanout,
        Payload payload,
        Sse sse,
        Slack slack
) {
    public record Pel(Duration claimMinIdle, int batchSize, Duration stuckTimeout, List<Integer> backoffMinutes, int maxStuckRetry) {}

    public record Stream(int maxLen) {}

    public record Scheduler(String reservedDispatchCron, String streamTrimCron, String stuckRecoveryCron, String pelReclaimCron, Duration leaderLockTtl) {}

    public record Channel(int maxPerMember) {}

    public record Fanout(int chunkSize) {}

    public record Payload(long gameStartOffsetMinutes) {}

    public record Sse(long timeoutMs) {}

    public record Slack(String webhookUrl, String signingSecret, Duration suppressTtl) {}
}
