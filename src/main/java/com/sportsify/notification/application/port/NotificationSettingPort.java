package com.sportsify.notification.application.port;

import com.sportsify.common.notification.NotificationEventType;

public interface NotificationSettingPort {
    boolean isAlertEnabled(Long memberId, NotificationEventType eventType);
}
