package com.sportsify.notification.domain.model;

import com.sportsify.common.notification.NotificationEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "ticket_open_alert", nullable = false)
    private boolean ticketOpenAlert;

    @Column(name = "game_start_alert", nullable = false)
    private boolean gameStartAlert;

    @Column(name = "payment_alert", nullable = false)
    private boolean paymentAlert;

    @Column(name = "chat_mention_alert", nullable = false)
    private boolean chatMentionAlert;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static NotificationSetting createDefault(Long memberId) {
        NotificationSetting setting = new NotificationSetting();
        setting.memberId = memberId;
        setting.ticketOpenAlert = true;
        setting.gameStartAlert = true;
        setting.paymentAlert = true;
        setting.chatMentionAlert = true;
        return setting;
    }

    public void update(boolean ticketOpenAlert, boolean gameStartAlert, boolean paymentAlert, boolean chatMentionAlert) {
        this.ticketOpenAlert = ticketOpenAlert;
        this.gameStartAlert = gameStartAlert;
        this.paymentAlert = paymentAlert;
        this.chatMentionAlert = chatMentionAlert;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEnabledFor(NotificationEventType eventType) {
        return switch (eventType) {
            case TICKET_OPEN -> ticketOpenAlert;
            case GAME_START -> gameStartAlert;
            case PAYMENT_COMPLETED -> paymentAlert;
            case CHAT_MENTION -> chatMentionAlert;
        };
    }
}
