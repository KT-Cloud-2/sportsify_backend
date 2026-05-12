package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import com.sportsify.notification.domain.repository.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationChannelRepositoryAdapter implements NotificationChannelRepository {
    private final NotificationChannelJpaRepository jpaRepository;

    @Override
    public NotificationChannel save(NotificationChannel channel) {
        return jpaRepository.save(channel);
    }

    @Override
    public Optional<NotificationChannel> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType) {
        return jpaRepository.findByMemberIdAndChannelType(memberId, channelType);
    }

    @Override
    public List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId) {
        return jpaRepository.findByMemberIdAndEnabledTrue(memberId);
    }

    @Override
    public boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType) {
        return jpaRepository.existsByMemberIdAndChannelType(memberId, channelType);
    }

    @Override
    public void delete(NotificationChannel channel) {
        jpaRepository.delete(channel);
    }
}
