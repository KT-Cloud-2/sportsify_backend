package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationHistory;
import com.sportsify.notification.domain.repository.NotificationHistoryRepository;
import java.util.List;
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

    @Override
    public List<NotificationHistory> saveAll(List<NotificationHistory> histories) {
        return jpaRepository.saveAll(histories);
    }
}
