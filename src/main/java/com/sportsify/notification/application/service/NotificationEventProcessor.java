package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventProcessor {

    private final NotificationEventRepository eventRepository;
    private final NotificationFanoutService fanoutService;

    public void process(NotificationEventType eventType, String payload) {
        NotificationEvent event = saveEvent(eventType, payload);
        boolean anyFailed = fanoutService.fanout(event, eventType, payload);
        markEventStatus(event.getId(), anyFailed);
    }

    @Transactional
    public NotificationEvent saveEvent(NotificationEventType eventType, String payload) {
        return eventRepository.save(NotificationEvent.create(eventType, payload));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventStatus(Long eventId, boolean anyFailed) {
        eventRepository.findById(eventId).ifPresent(event -> {
            if (anyFailed) {
                event.markFailed();
            } else {
                event.markPublished();
            }
        });
    }
}
