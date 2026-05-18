package com.sportsify.ticketing.domain.model;

import com.sportsify.member.domain.model.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderSeat> orderSeats = new ArrayList<>();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Order(Member member) {
        this.member = member;
        this.status = OrderStatus.PENDING;
        this.expiresAt = LocalDateTime.now().plusMinutes(15);
    }

    public static Order create(Member member) {
        return new Order(member);
    }

    public void addOrderSeat(OrderSeat orderSeat) {
        this.orderSeats.add(orderSeat);
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public void updateExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getMemberId() {
        return member.getId();
    }


    public void expire() {
        status = OrderStatus.EXPIRED;
        orderSeats.forEach(OrderSeat::expire);
    }

    public void cancel() {
        status = OrderStatus.CANCELLED;
        orderSeats.forEach(OrderSeat::cancel);
    }

}
