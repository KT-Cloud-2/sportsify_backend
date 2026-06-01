package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.GameSeat;
import com.sportsify.game.domain.model.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
    @Query("SELECT COUNT(gs) FROM GameSeat gs WHERE gs.game.id = :gameId AND gs.seatStatus = :seatStatus ")
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

    @Query("""
             SELECT gs FROM GameSeat gs
             WHERE gs.id IN :ids AND gs.seatStatus = 'AVAILABLE' ORDER BY gs.id
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GameSeat> findAllAvailableIdsWithLock(@Param("ids") List<Long> ids);

    @Query("SELECT gs FROM GameSeat gs WHERE gs.game.id = :gameId")
    List<GameSeat> findByGameId(Long gameId);

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE GameSeat gs SET gs.seatStatus = 'AVAILABLE'
                WHERE gs.id IN (
                    SELECT os.gameSeat.id FROM OrderSeat os WHERE os.order.id IN :orderIds
                )
            """)
    void bulkReleaseGameSeatsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
