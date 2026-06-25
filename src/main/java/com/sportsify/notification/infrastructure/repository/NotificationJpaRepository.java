package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);

    @Query("SELECT n.memberId FROM Notification n WHERE n.eventId = :eventId AND n.memberId IN :memberIds")
    Set<Long> findMemberIdsByEventIdAndMemberIdIn(@Param("eventId") Long eventId, @Param("memberIds") List<Long> memberIds);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.memberId = :memberId AND n.read = false")
    void markAllReadByMemberId(@Param("memberId") Long memberId);
}
