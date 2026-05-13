package com.sportsify.notification.application.dto;

import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.common.notification.NotificationEventType;

import java.time.LocalDateTime;

public record NotificationResult(
        Long id,
        NotificationEventType eventType,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResult of(Notification notification, NotificationEvent event) {
        return new NotificationResult(
                notification.getId(),
                event.getEventType(),
                event.getPayload(),
                notification.isAlreadyRead(),
                notification.getCreatedAt()
        );
    }
}
