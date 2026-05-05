package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.memberId = :memberId AND n.read = false")
    void markAllReadByMemberId(Long memberId);
}
