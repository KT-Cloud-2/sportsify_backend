package com.sportsify.notification.application.port;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationSetting;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface SseNotificationPort {
    SseEmitter subscribe(Long memberId, NotificationSetting setting, List<NotificationChannel> channels);
    void send(Long memberId, Object data);
    List<NotificationChannel> getCachedChannels(Long memberId);
}
