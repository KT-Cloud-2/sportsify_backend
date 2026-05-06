package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface NotificationSettingJpaRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByMemberId(Long memberId);

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.ticketOpenAlert = true")
    List<Long> findMemberIdsByTicketOpenAlertTrue();

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.gameStartAlert = true")
    List<Long> findMemberIdsByGameStartAlertTrue();

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.paymentAlert = true")
    List<Long> findMemberIdsByPaymentAlertTrue();

    @Query("SELECT s.memberId FROM NotificationSetting s WHERE s.chatMentionAlert = true")
    List<Long> findMemberIdsByChatMentionAlertTrue();
}
