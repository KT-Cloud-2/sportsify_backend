package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannel, Long> {
    Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId);
    boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    int countByMemberId(Long memberId);
}
