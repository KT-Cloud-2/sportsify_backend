package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository {
    NotificationChannel save(NotificationChannel channel);
    Optional<NotificationChannel> findById(Long id);
    Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId);
    boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    int countByMemberId(Long memberId);
    void delete(NotificationChannel channel);
}
