package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_channels",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "channel_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private NotificationChannelType channelType;

    @Column(name = "channel_target", nullable = false)
    private String channelTarget;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static NotificationChannel create(Long memberId, NotificationChannelType channelType, String channelTarget) {
        NotificationChannel channel = new NotificationChannel();
        channel.memberId = memberId;
        channel.channelType = channelType;
        channel.channelTarget = channelTarget;
        channel.enabled = true;
        channel.createdAt = LocalDateTime.now();
        return channel;
    }

    public void toggle() {
        this.enabled = !this.enabled;
        this.updatedAt = LocalDateTime.now();
    }
}
