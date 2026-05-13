package com.sportsify.common.notification;

import com.sportsify.common.notification.payload.NotificationPayload;

public interface NotificationEventPublisher {

    void publish(NotificationEventType eventType, NotificationPayload payload);
}
