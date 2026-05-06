package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import com.sportsify.notification.domain.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
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
    public List<Long> findMemberIdsByTicketOpenAlertTrue() {
        return jpaRepository.findMemberIdsByTicketOpenAlertTrue();
    }

    @Override
    public List<Long> findMemberIdsByGameStartAlertTrue() {
        return jpaRepository.findMemberIdsByGameStartAlertTrue();
    }

    @Override
    public List<Long> findMemberIdsByPaymentAlertTrue() {
        return jpaRepository.findMemberIdsByPaymentAlertTrue();
    }

    @Override
    public List<Long> findMemberIdsByChatMentionAlertTrue() {
        return jpaRepository.findMemberIdsByChatMentionAlertTrue();
    }
}
