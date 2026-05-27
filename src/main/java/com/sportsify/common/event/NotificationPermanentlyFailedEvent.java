package com.sportsify.common.event;

import com.sportsify.common.notification.NotificationEventType;

import java.time.LocalDateTime;

public record NotificationPermanentlyFailedEvent(
        Long eventId,
        NotificationEventType eventType,
        int retryCount,
        String source,
        LocalDateTime occurredAt
) {
    public static NotificationPermanentlyFailedEvent of(Long eventId, NotificationEventType eventType, int retryCount, String source) {
        return new NotificationPermanentlyFailedEvent(eventId, eventType, retryCount, source, LocalDateTime.now());
    }
}
