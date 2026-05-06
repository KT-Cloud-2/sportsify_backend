package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_history")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private NotificationChannelType channelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationSendStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static NotificationHistory sent(Long notificationId, NotificationChannelType channelType) {
        NotificationHistory history = new NotificationHistory();
        history.notificationId = notificationId;
        history.channelType = channelType;
        history.status = NotificationSendStatus.SENT;
        history.errorMessage = "";
        return history;
    }

    public static NotificationHistory failed(Long notificationId, NotificationChannelType channelType, String errorMessage) {
        NotificationHistory history = new NotificationHistory();
        history.notificationId = notificationId;
        history.channelType = channelType;
        history.status = NotificationSendStatus.FAILED;
        history.errorMessage = errorMessage;
        return history;
    }
}
