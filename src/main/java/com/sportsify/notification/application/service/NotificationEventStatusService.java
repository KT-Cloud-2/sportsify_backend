package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationEventStatusService {

    private final NotificationEventRepository eventRepository;

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
