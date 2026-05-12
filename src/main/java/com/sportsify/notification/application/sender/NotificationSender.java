package com.sportsify.notification.application.sender;

import com.sportsify.notification.domain.model.NotificationChannelType;

public interface NotificationSender {
    NotificationChannelType channelType();
    void send(String target, String subject, String body);
}
