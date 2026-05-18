package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findByDeletedAtIsNullOrderByStartAt();
}
