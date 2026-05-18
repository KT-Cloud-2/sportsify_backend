package com.sportsify.game.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stadiums")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stadium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(name = "total_seats")
    private Integer totalSeats;

    @Builder
    public Stadium(String name, String address, Integer totalSeats) {
        this.name = name;
        this.address = address;
        this.totalSeats = totalSeats;
    }
}