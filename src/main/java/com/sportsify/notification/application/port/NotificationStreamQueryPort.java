package com.sportsify.notification.application.port;

import com.sportsify.common.notification.NotificationEventType;

import java.util.Optional;

public interface NotificationStreamQueryPort {
    Optional<String> findPayload(NotificationEventType eventType, String messageId);
}
