package com.sportsify.notification.domain.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.Optional;

public interface NotificationSettingRepository {
    NotificationSetting save(NotificationSetting setting);
    Optional<NotificationSetting> findByMemberId(Long memberId);
    Slice<Long> findMemberIdsByTicketOpenAlertTrue(Pageable pageable);
    Slice<Long> findMemberIdsByGameStartAlertTrue(Pageable pageable);
    Slice<Long> findMemberIdsByPaymentAlertTrue(Pageable pageable);
    Slice<Long> findMemberIdsByChatMentionAlertTrue(Pageable pageable);
}
