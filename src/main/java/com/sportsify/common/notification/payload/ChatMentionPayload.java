package com.sportsify.common.notification.payload;

public record ChatMentionPayload(
        Long roomId,
        Long memberId
) implements NotificationPayload {}
