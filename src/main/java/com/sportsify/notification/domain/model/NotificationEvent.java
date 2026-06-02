package com.sportsify.notification.domain.model;

import com.sportsify.common.notification.NotificationEventType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationEventStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "stream_message_id", unique = true)
    private String streamMessageId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "stuck_retry_count", nullable = false)
    private int stuckRetryCount = 0;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static NotificationEvent create(NotificationEventType eventType, String payload) {
        NotificationEvent event = new NotificationEvent();
        event.eventType = eventType;
        event.payload = payload;
        event.status = NotificationEventStatus.PENDING;
        return event;
    }

    public static NotificationEvent createScheduled(NotificationEventType eventType, String payload, LocalDateTime scheduledAt) {
        NotificationEvent event = create(eventType, payload);
        event.scheduledAt = scheduledAt;
        return event;
    }

    public static NotificationEvent withId(Long id, NotificationEventType eventType, String payload) {
        NotificationEvent event = create(eventType, payload);
        event.id = id;
        return event;
    }

    public static NotificationEvent scheduledWithId(Long id, NotificationEventType eventType, String payload, LocalDateTime scheduledAt) {
        NotificationEvent event = createScheduled(eventType, payload, scheduledAt);
        event.id = id;
        return event;
    }

    public void assignStreamMessageId(String streamMessageId) {
        this.streamMessageId = streamMessageId;
    }

    public boolean isScheduled() {
        return scheduledAt != null;
    }

    public boolean hasStreamMessageId() {
        return streamMessageId != null;
    }

    public void markPending() {
        this.status = NotificationEventStatus.PENDING;
    }

    public void markProcessing() {
        this.status = NotificationEventStatus.PROCESSING;
    }

    public void markPublished() {
        this.status = NotificationEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationEventStatus.FAILED;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public void incrementStuckRetry() {
        this.stuckRetryCount++;
    }

    public boolean incrementRetryAndCheckExhausted(int backoffSize) {
        this.retryCount++;
        return this.retryCount >= backoffSize;
    }

    public void markPermanentlyFailed() {
        this.status = NotificationEventStatus.PERMANENTLY_FAILED;
    }

    public void markCancelled() {
        this.status = NotificationEventStatus.CANCELLED;
    }

    public String getTypeName(){
        return this.eventType.name();
    }
}
