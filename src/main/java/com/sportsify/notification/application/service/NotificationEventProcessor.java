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
        boolean anyFailed = fanoutService.fanout(event, eventType, payload);
        statusService.markEventStatus(event.getId(), anyFailed);
    }
}
