package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.Notification;
import com.sportsify.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {
    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findByIdAndMemberId(Long id, Long memberId) {
        return jpaRepository.findByIdAndMemberId(id, memberId);
    }

    @Override
    public Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable) {
        return jpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    @Override
    public boolean existsByEventIdAndMemberId(Long eventId, Long memberId) {
        return jpaRepository.existsByEventIdAndMemberId(eventId, memberId);
    }

    @Override
    public void markAllReadByMemberId(Long memberId) {
        jpaRepository.markAllReadByMemberId(memberId);
    }
}
