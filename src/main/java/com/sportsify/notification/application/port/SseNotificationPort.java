package com.sportsify.notification.application.port;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseNotificationPort {
    SseEmitter subscribe(Long memberId);
    void send(Long memberId, Object data);
}
