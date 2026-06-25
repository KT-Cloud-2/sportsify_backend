package com.sportsify.notification.domain.model;

import org.springframework.data.redis.connection.stream.RecordId;

public record NotificationBufferItem(
        Notification notification,
        String subject,
        String payload,
        String streamKey,
        RecordId recordId
) {
    public static NotificationBufferItem of(Notification notification, String subject, String payload,
                                             String streamKey, RecordId recordId) {
        return new NotificationBufferItem(notification, subject, payload, streamKey, recordId);
    }
}
