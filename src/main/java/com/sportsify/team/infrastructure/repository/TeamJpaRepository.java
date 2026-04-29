package com.sportsify.team.infrastructure.repository;

import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamJpaRepository extends JpaRepository<Team, Long> {

    List<Team> findBySportTypeAndActive(SportType sportType, boolean active);

    List<Team> findByActive(boolean active);

    List<Team> findBySportType(SportType sportType);
}
