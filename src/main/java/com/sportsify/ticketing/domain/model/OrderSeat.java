package com.sportsify.ticketing.domain.model;

import com.sportsify.game.domain.model.GameSeat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OrderSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_seat_id", nullable = false)
    private GameSeat gameSeat;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderSeatStatus status;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    private OrderSeat(Order order, GameSeat gameSeat) {
        this.order = order;
        this.gameSeat = gameSeat;
        this.status = OrderSeatStatus.HOLDING;
    }

    public static OrderSeat create(Order order, GameSeat gameSeat) {
        return new OrderSeat(order, gameSeat);
    }

    public void updateStatus(OrderSeatStatus status) {
        this.status = status;
    }

    public Long getSeatId() {
        return gameSeat.getSeatId();
    }

    public String getSeatGradeName() {
        return gameSeat.getZoneGradeName();
    }

    public String getSectionName() {
        return gameSeat.getSectionName();
    }

    public Integer getSeatPrice() {
        return gameSeat.getPrice();
    }
}