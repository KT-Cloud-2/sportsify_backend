package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationHistory;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationHistoryRepositoryAdapter implements NotificationHistoryRepository {
    private final NotificationHistoryJpaRepository jpaRepository;

    @Override
    public NotificationHistory save(NotificationHistory history) {
        return jpaRepository.save(history);
    }
}
