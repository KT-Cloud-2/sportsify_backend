package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationEventRepository {
    NotificationEvent save(NotificationEvent event);
    Optional<NotificationEvent> findById(Long id);
    Optional<NotificationEvent> findByStreamMessageId(String streamMessageId);
    List<NotificationEvent> findAllById(List<Long> ids);
    List<NotificationEvent> findDueScheduledEventsForUpdate(LocalDateTime now, int maxRetry);
    List<NotificationEvent> findStuckProcessingEventsForUpdate(LocalDateTime updatedBefore, int maxStuckRetry);
}
