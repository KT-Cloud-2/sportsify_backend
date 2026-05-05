package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEventType eventType;

    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationEventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static NotificationEvent create(NotificationEventType eventType, String payload) {
        NotificationEvent event = new NotificationEvent();
        event.eventType = eventType;
        event.payload = payload;
        event.status = NotificationEventStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public void markPublished() {
        this.status = NotificationEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationEventStatus.FAILED;
    }
}
