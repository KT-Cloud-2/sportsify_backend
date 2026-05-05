package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import com.sportsify.notification.domain.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
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
}
