package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository {
    NotificationSetting save(NotificationSetting setting);
    Optional<NotificationSetting> findByMemberId(Long memberId);
    List<Long> findMemberIdsByTicketOpenAlertTrue();
    List<Long> findMemberIdsByGameStartAlertTrue();
    List<Long> findMemberIdsByPaymentAlertTrue();
}
