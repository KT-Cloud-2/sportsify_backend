package com.sportsify.game.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "zone_grades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", nullable = false)
    private Stadium stadium;

    @Column(nullable = false, length = 30)
    private String name;

    @Builder
    public ZoneGrade(Stadium stadium, String name) {
        this.stadium = stadium;
        this.name = name;
    }
}