package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationHistory;

public interface NotificationHistoryRepository {
    NotificationHistory save(NotificationHistory history);
}
