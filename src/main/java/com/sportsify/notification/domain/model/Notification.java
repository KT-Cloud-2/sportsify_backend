package com.sportsify.notification.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "member_id"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(name = "member_id", nullable = false)
    @Getter
    private Long memberId;

    @Column(name = "event_id", nullable = false)
    @Getter
    private Long eventId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Getter
    private LocalDateTime createdAt;

    public static Notification create(Long memberId, Long eventId) {
        Notification notification = new Notification();
        notification.memberId = memberId;
        notification.eventId = eventId;
        notification.read = false;
        return notification;
    }

    public static Notification withId(Long id, Long memberId, Long eventId) {
        Notification notification = create(memberId, eventId);
        notification.id = id;
        return notification;
    }

    public void markRead() {
        this.read = true;
    }

    public boolean isAlreadyRead() {
        return this.read;
    }
}
