package com.sportsify.notification.application.service;

import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
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

    // FOR UPDATE SKIP LOCKED는 READ_COMMITTED에서만 의도대로 동작
    // REPEATABLE_READ 이상이면 스냅샷 기준으로 동작해 skip 의미가 달라짐
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<NotificationEvent> claimDueEvents() {
        List<NotificationEvent> events = eventRepository.findDueScheduledEventsForUpdate(LocalDateTime.now());
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
