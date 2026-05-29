package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import com.sportsify.notification.infrastructure.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final NotificationEventRepository eventRepository;
    private final PayloadParser payloadParser;
    private final NotificationProperties properties;

    @Transactional
    public NotificationEvent saveEventWithStreamMessageId(NotificationEventType eventType, String payload, String streamMessageId) {
        return eventRepository.findByStreamMessageId(streamMessageId)
                .orElseGet(() -> {
                    NotificationEvent event = buildEvent(eventType, payload);
                    event.assignStreamMessageId(streamMessageId);
                    return eventRepository.save(event);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void markEventStatus(Long eventId, boolean anyFailed) {
        eventRepository.findById(eventId).ifPresentOrElse(
                event -> applyStatus(event, anyFailed),
                () -> log.warn("마킹할 이벤트를 찾을 수 없음 eventId={}", eventId)
        );
    }

    private NotificationEvent buildEvent(NotificationEventType eventType, String payload) {
        if (eventType.isScheduled()) {
            Optional<LocalDateTime> scheduledAt = payloadParser.extractScheduledAt(payload, eventType);
            if (scheduledAt.isPresent()) {
                return NotificationEvent.createScheduled(eventType, payload, scheduledAt.get());
            }
        }
        return NotificationEvent.create(eventType, payload);
    }

    private void applyStatus(NotificationEvent event, boolean anyFailed) {
        if (anyFailed) {
            boolean exhausted = event.incrementRetryAndCheckExhausted(properties.pel().backoffMinutes().size());
            if (exhausted) {
                event.markPermanentlyFailed();
            } else {
                event.markFailed();
            }
            eventRepository.save(event);
            return;
        }
        event.markPublished();
        eventRepository.save(event);
    }
}
