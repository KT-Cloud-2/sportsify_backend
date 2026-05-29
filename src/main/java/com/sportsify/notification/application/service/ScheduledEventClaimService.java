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
        // 스케줄 재시도 한도는 PEL backoff 단계 수와 동일하게 유지 (backoffMinutes 변경 시 함께 검토)
        List<NotificationEvent> events = eventRepository.findDueScheduledEventsForUpdate(
                LocalDateTime.now(), properties.pel().backoffMinutes().size());
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
