package com.sportsify.game.domain.model;


import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;


@Entity
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", nullable = false)
    private Stadium stadium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", length = 30)
    private SportType sportType;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 180;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", length = 10)
    private DayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_grade", length = 20)
    private GameGrade gameGrade;

    @Column(name = "max_ticket_per_user", nullable = false)
    private Integer maxTicketPerUser = 4;

    @Column(name = "sale_start_at")
    private LocalDateTime saleStartAt;

    @Column(name = "sale_end_at")
    private LocalDateTime saleEndAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Game(Stadium stadium, Team homeTeam, Team awayTeam, SportType sportType,
                LocalDateTime startAt, Integer durationMinutes, GameStatus status,
                DayType dayType, GameGrade gameGrade, Integer maxTicketPerUser,
                LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
        this.stadium = stadium;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.sportType = sportType;
        this.startAt = startAt;
        this.durationMinutes = durationMinutes != null ? durationMinutes : 180;
        this.status = status;
        this.dayType = dayType;
        this.gameGrade = gameGrade;
        this.maxTicketPerUser = maxTicketPerUser != null ? maxTicketPerUser : 4;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
    }

}