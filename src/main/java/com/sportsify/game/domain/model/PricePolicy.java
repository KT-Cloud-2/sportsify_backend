package com.sportsify.game.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", nullable = false)
    private Stadium stadium;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_grade_id", nullable = false)
    private ZoneGrade zoneGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_grade", nullable = false, length = 20)
    private GameGrade gameGrade;

    @Column(nullable = false)
    private Integer price;

    @Builder
    public PricePolicy(Stadium stadium, DayType dayType, ZoneGrade zoneGrade,
                       GameGrade gameGrade, Integer price) {
        this.stadium = stadium;
        this.dayType = dayType;
        this.zoneGrade = zoneGrade;
        this.gameGrade = gameGrade;
        this.price = price;
    }


}