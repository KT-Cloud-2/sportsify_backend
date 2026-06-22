package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationSettingRepositoryAdapter implements NotificationSettingRepository {
    private final NotificationSettingJpaRepository jpaRepository;

    @Override
    @CacheEvict(value = "notificationSettings", key = "#setting.memberId")
    public NotificationSetting save(NotificationSetting setting) {
        return jpaRepository.save(setting);
    }

    @Override
    @Cacheable(value = "notificationSettings", key = "#memberId")
    public Optional<NotificationSetting> findByMemberId(Long memberId) {
        return jpaRepository.findByMemberId(memberId);
    }

    @Override
    public List<NotificationSetting> findByMemberIdIn(List<Long> memberIds) {
        return jpaRepository.findByMemberIdIn(memberIds);
    }

    @Override
    public Slice<Long> findMemberIdsByTicketOpenAlertTrue(Pageable pageable) {
        return jpaRepository.findMemberIdsByTicketOpenAlertTrue(pageable);
    }

    @Override
    public Slice<Long> findMemberIdsByGameStartAlertTrue(Pageable pageable) {
        return jpaRepository.findMemberIdsByGameStartAlertTrue(pageable);
    }

}
