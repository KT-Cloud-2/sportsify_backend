package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {
}
