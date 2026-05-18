package com.sportsify.game.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_grade_id", nullable = false)
    private ZoneGrade zoneGrade;

    @Column(name = "row_number", length = 10)
    private String rowNumber;

    @Column(name = "seat_number", length = 10)
    private String seatNumber;

    @Builder
    public Seat(Section section, String rowNumber, String seatNumber) {
        this.section = section;
        this.zoneGrade = section.getZoneGrade();
        this.rowNumber = rowNumber;
        this.seatNumber = seatNumber;
    }

}