package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface NotificationSettingJpaRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByMemberId(Long memberId);

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.ticketOpenAlert = true")
    Slice<Long> findMemberIdsByTicketOpenAlertTrue(Pageable pageable);

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.gameStartAlert = true")
    Slice<Long> findMemberIdsByGameStartAlertTrue(Pageable pageable);

}
