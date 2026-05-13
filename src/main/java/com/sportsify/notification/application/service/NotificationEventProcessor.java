package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventProcessor {

    private final NotificationEventStatusService statusService;
    private final NotificationFanoutService fanoutService;

    public void process(NotificationEventType eventType, String payload) {
        NotificationEvent event = statusService.saveEvent(eventType, payload);
        boolean anyFailed = true;
        try {
            anyFailed = fanoutService.fanout(event, eventType, payload);
        } catch (Exception e) {
            log.error("fanout 중 예외 발생 eventId={} eventType={}", event.getId(), eventType, e);
        } finally {
            statusService.markEventStatus(event.getId(), anyFailed);
        }
    }
}
