package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryJpaRepository extends JpaRepository<NotificationHistory, Long> {
}
