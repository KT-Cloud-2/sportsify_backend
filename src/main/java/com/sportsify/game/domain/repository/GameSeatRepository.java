package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
    int countByGameIdAndSeatStatus(Long gameId, SeatStatus seatStatus);

    @Query("""
            SELECT zg.name, gs.price, COUNT(gs)
            FROM GameSeat gs
            JOIN gs.seat s
            JOIN s.zoneGrade zg
            WHERE gs.game.id = :gameId AND gs.seatStatus = 'AVAILABLE'
            GROUP BY zg.name, gs.price
            """)
    List<Object[]> findSeatGradeSummaryByGameId(@Param("gameId") Long gameId);
}
