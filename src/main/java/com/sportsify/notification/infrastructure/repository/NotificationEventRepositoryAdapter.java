package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationEventRepositoryAdapter implements NotificationEventRepository {
    private final NotificationEventJpaRepository jpaRepository;

    @Override
    public NotificationEvent save(NotificationEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<NotificationEvent> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<NotificationEvent> findByStreamMessageId(String streamMessageId) {
        return jpaRepository.findByStreamMessageId(streamMessageId);
    }

    @Override
    public List<NotificationEvent> findAllById(List<Long> ids) {
        return jpaRepository.findAllById(ids);
    }

    @Override
    public List<NotificationEvent> findDueScheduledEventsForUpdate(LocalDateTime now) {
        return jpaRepository.findDueScheduledEventsForUpdate(now);
    }

    @Override
    public List<NotificationEvent> findStuckProcessingEventsForUpdate(LocalDateTime updatedBefore) {
        return jpaRepository.findStuckProcessingEventsForUpdate(updatedBefore);
    }
}
