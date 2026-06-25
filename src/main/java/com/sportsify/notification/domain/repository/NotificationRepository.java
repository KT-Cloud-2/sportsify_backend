package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationRepository {
    Notification save(Notification notification);
    List<Notification> saveAll(List<Notification> notifications);
    Set<Long> findExistingMemberIdsByEventId(Long eventId, List<Long> memberIds);
    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);
    void markAllReadByMemberId(Long memberId);
}
