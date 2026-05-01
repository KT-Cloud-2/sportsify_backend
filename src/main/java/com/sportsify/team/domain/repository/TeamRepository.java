package com.sportsify.team.domain.repository;

import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;

import java.util.List;
import java.util.Optional;

public interface TeamRepository {

    List<Team> findBySportTypeAndActive(SportType sportType, boolean active);

    List<Team> findByActive(boolean active);

    List<Team> findBySportType(SportType sportType);

    List<Team> findAll();

    Optional<Team> findById(Long id);
}
