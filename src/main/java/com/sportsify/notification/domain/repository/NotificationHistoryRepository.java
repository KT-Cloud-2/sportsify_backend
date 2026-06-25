package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationHistory;
import java.util.List;

public interface NotificationHistoryRepository {
    NotificationHistory save(NotificationHistory history);
    List<NotificationHistory> saveAll(List<NotificationHistory> histories);
}
