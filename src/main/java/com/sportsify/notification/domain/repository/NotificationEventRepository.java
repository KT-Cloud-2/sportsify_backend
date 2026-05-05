package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import java.util.Optional;

public interface NotificationEventRepository {
    NotificationEvent save(NotificationEvent event);
    Optional<NotificationEvent> findById(Long id);
}
