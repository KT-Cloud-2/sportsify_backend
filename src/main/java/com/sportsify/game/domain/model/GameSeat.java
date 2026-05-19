package com.sportsify.game.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_status", nullable = false, length = 20)
    private SeatStatus seatStatus;

    @Column(nullable = false)
    private Integer price;

    @Builder
    public GameSeat(Game game, Seat seat, Integer price) {
        this.game = game;
        this.seat = seat;
        this.seatStatus = SeatStatus.AVAILABLE;
        this.price = price;
    }

    public void updateSeatStatus(SeatStatus status) {
        this.seatStatus = status;
    }

    public String getZoneGradeName() {
        return seat.getZoneGrade().getName();
    }

    public String getSectionName() {
        return seat.getSection().getName();
    }

    public Long getSeatId() {
        return seat.getId();
    }

    // === 디미터 법칙 위임 메서드 ===
    
    public String getSeatNumber() {
        return seat.getSeatNumber();
    }

    public Long getGameId() {
        return game.getId();
    }

    public String getSportType() {
        return game.getSportTypeName();
    }

    public String getHomeTeamName() {
        return game.getHomeTeamName();
    }

    public String getAwayTeamName() {
        return game.getAwayTeamName();
    }

    public LocalDateTime getStartAt() {
        return game.getStartAt();
    }

    public String getStadiumName() {
        return game.getStadiumName();
    }

}
