package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.notification.domain.model.NotificationEventType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationEventType eventType,
        String payload,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(
                result.id(),
                result.eventType(),
                result.payload(),
                result.read(),
                result.createdAt()
        );
    }
}
