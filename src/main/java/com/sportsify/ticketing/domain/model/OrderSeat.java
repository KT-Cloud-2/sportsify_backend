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
    @Column(nullable = false, length = 30)
    private OrderSeatStatus status;

    @Column(nullable = false)
    private Integer price;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    private OrderSeat(Order order, GameSeat gameSeat, Integer price) {
        this.order = order;
        this.gameSeat = gameSeat;
        this.status = OrderSeatStatus.HOLDING;
        this.price = price;
    }

    public static OrderSeat create(Order order, GameSeat gameSeat, Integer price) {
        return new OrderSeat(order, gameSeat, price);
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

    // === 디미터 법칙 위임 메서드 ===

    public String getSeatNumber() {
        return gameSeat.getSeatNumber();
    }

    public Long getGameId() {
        return gameSeat.getGameId();
    }

    public String getSportType() {
        return gameSeat.getSportType();
    }

    public String getHomeTeamName() {
        return gameSeat.getHomeTeamName();
    }

    public String getAwayTeamName() {
        return gameSeat.getAwayTeamName();
    }

    public LocalDateTime getStartAt() {
        return gameSeat.getStartAt();
    }

    public String getStadiumName() {
        return gameSeat.getStadiumName();
    }
}
