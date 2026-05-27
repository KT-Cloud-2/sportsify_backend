package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledEventClaimService {

    private final NotificationEventRepository eventRepository;
    private final NotificationProperties properties;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<NotificationEvent> claimDueEvents() {
        List<NotificationEvent> events = eventRepository.findDueScheduledEventsForUpdate(
                LocalDateTime.now(), properties.retry().maxRetry());
        events.forEach(event -> {
            event.markProcessing();
            eventRepository.save(event);
        });
        return events;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<NotificationEvent> claimStuckEvents(LocalDateTime stuckBefore) {
        List<NotificationEvent> events = eventRepository.findStuckProcessingEventsForUpdate(stuckBefore);
        events.forEach(event -> {
            event.markPending();
            eventRepository.save(event);
        });
        return events;
    }
}
