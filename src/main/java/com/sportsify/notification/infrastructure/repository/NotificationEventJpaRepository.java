package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEvent, Long> {

    @Query("SELECT e FROM NotificationEvent e WHERE e.id IN :ids")
    List<NotificationEvent> findAllByIdIn(@Param("ids") List<Long> ids);
}
