package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEvent, Long> {

    Optional<NotificationEvent> findByStreamMessageId(String streamMessageId);

    @Query(value = "SELECT * FROM notification_events WHERE status IN ('PENDING', 'FAILED') AND scheduled_at <= :now AND retry_count < :maxRetry FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<NotificationEvent> findDueScheduledEventsForUpdate(@Param("now") LocalDateTime now, @Param("maxRetry") int maxRetry);

    @Query(value = "SELECT * FROM notification_events WHERE status = 'PROCESSING' AND updated_at <= :updatedBefore AND stuck_retry_count < :maxStuckRetry FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<NotificationEvent> findStuckProcessingEventsForUpdate(@Param("updatedBefore") LocalDateTime updatedBefore, @Param("maxStuckRetry") int maxStuckRetry);

}
