package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEvent, Long> {
}
