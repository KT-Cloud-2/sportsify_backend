package com.sportsify.notification.application.service;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
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
public class NotificationEventStatusService {

    private final NotificationEventRepository eventRepository;
    private final NotificationPayloadParser payloadParser;

    @Transactional
    public NotificationEvent saveEvent(NotificationEventType eventType, String payload) {
        if (eventType.isScheduled()) {
            Optional<LocalDateTime> scheduledAt = payloadParser.extractScheduledAt(payload, eventType);
            if (scheduledAt.isPresent()) {
                return eventRepository.save(NotificationEvent.createScheduled(eventType, payload, scheduledAt.get()));
            }
        }
        return eventRepository.save(NotificationEvent.create(eventType, payload));
    }

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

    @Transactional
    public boolean incrementScheduledRetry(Long eventId, int maxRetry) {
        return eventRepository.findById(eventId)
                .map(event -> {
                    boolean exhausted = event.incrementRetryAndCheckExhausted(maxRetry);
                    if (exhausted) {
                        event.markPermanentlyFailed();
                    } else {
                        event.markFailed();
                    }
                    eventRepository.save(event);
                    return exhausted;
                })
                .orElse(false);
    }

    private void applyStatus(NotificationEvent event, boolean anyFailed) {
        if (anyFailed) {
            event.markFailed();
            eventRepository.save(event);
            return;
        }
        event.markPublished();
        eventRepository.save(event);
    }
}
