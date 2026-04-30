package com.sportsify.team.infrastructure.repository;

import com.sportsify.team.domain.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
