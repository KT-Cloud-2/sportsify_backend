package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationSettingRepositoryAdapter implements NotificationSettingRepository {
    private final NotificationSettingJpaRepository jpaRepository;

    @Override
    public NotificationSetting save(NotificationSetting setting) {
        return jpaRepository.save(setting);
    }

    @Override
    public Optional<NotificationSetting> findByMemberId(Long memberId) {
        return jpaRepository.findByMemberId(memberId);
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
