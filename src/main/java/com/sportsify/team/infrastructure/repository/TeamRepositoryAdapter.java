package com.sportsify.team.infrastructure.repository;

import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TeamRepositoryAdapter implements TeamRepository {

    private final TeamJpaRepository jpaRepository;

    @Override
    public List<Team> findBySportTypeAndActive(SportType sportType, boolean active) {
        return jpaRepository.findBySportTypeAndActive(sportType, active);
    }

    @Override
    public List<Team> findByActive(boolean active) {
        return jpaRepository.findByActive(active);
    }

    @Override
    public List<Team> findBySportType(SportType sportType) {
        return jpaRepository.findBySportType(sportType);
    }

    @Override
    public List<Team> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Optional<Team> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
