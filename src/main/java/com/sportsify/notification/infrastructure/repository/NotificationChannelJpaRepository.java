package com.sportsify.notification.infrastructure.repository;

import com.sportsify.notification.domain.model.NotificationChannel;
import com.sportsify.notification.domain.model.NotificationChannelType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannel, Long> {
    Optional<NotificationChannel> findByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    List<NotificationChannel> findByMemberIdAndEnabledTrue(Long memberId);
    boolean existsByMemberIdAndChannelType(Long memberId, NotificationChannelType channelType);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM NotificationChannel c WHERE c.memberId = :memberId")
    List<NotificationChannel> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
