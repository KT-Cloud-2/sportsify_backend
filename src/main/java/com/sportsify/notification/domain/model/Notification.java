package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Notification create(Long memberId, Long eventId) {
        Notification notification = new Notification();
        notification.memberId = memberId;
        notification.eventId = eventId;
        notification.read = false;
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public void markRead() {
        this.read = true;
    }

    public boolean isAlreadyRead() {
        return this.read;
    }
}
