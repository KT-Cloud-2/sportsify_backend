package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
