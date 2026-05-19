package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findByDeletedAtIsNullOrderByStartAt();

    @Query("SELECT g FROM Game g " +
            "WHERE g.status = 'SCHEDULED' " +
            "AND g.saleStartAt <= :now " +
            "AND (g.saleEndAt IS NULL OR g.saleEndAt > :now) " +
            "AND g.deletedAt IS NULL")
    List<Game> findGamesReadyForSale(@Param("now") LocalDateTime now);

    @Query("SELECT g FROM Game g " +
            "WHERE g.status = 'SCHEDULED' " +
            "AND g.saleStartAt > :now " +
            "AND g.deletedAt IS NULL")
    List<Game> findUpcomingScheduledGames(@Param("now") LocalDateTime now);

    @Query("SELECT g FROM Game g " +
            "WHERE g.status = 'ON_SALE' " +
            "AND g.saleEndAt <= :now " +
            "AND g.deletedAt IS NULL")
    List<Game> findGamesToCloseSale(@Param("now") LocalDateTime now);

    @Query("SELECT g FROM Game g " +
            "WHERE g.status = 'ON_SALE' " +
            "AND g.saleEndAt > :now " +
            "AND g.deletedAt IS NULL")
    List<Game> findOnSaleGamesWithFutureSaleEnd(@Param("now") LocalDateTime now);
}
