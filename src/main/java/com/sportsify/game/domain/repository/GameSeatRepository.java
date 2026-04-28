package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
    int countByGameIdAndSeatStatus(Long gameId, SeatStatus seatStatus);
}
