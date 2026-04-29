package com.sportsify.payment.domain.entity;

import com.sportsify.payment.domain.type.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    private String orderId;

    @Column(name = "payment_key", unique = true, length = 100)
    private String paymentKey;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Payment(
            Long userId,
            Long matchId,
            Long seatId,
            String orderId,
            String paymentKey,
            String idempotencyKey,
            Long amount,
            String paymentMethod,
            PaymentStatus status,
            LocalDateTime requestedAt,
            LocalDateTime approvedAt
    ) {
        this.userId = userId;
        this.matchId = matchId;
        this.seatId = seatId;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
    }

    public void markCompleted(String paymentKey, String paymentMethod, LocalDateTime approvedAt) {
        this.paymentKey = paymentKey;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = approvedAt;
    }
}