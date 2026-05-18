package com.sportsify.game.domain.repository;

import com.sportsify.game.domain.model.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {
}
