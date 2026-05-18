package com.sportsify.team.application.service;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.team.application.dto.TeamResult;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;

    public List<TeamResult> getTeams(String sportType, Boolean isActive) {
        List<com.sportsify.team.domain.model.Team> teams;

        SportType sportTypeEnum = parseSportType(sportType);

        if (sportTypeEnum != null && isActive != null) {
            teams = teamRepository.findBySportTypeAndActive(sportTypeEnum, isActive);
        } else if (sportTypeEnum != null) {
            teams = teamRepository.findBySportType(sportTypeEnum);
        } else if (isActive != null) {
            teams = teamRepository.findByActive(isActive);
        } else {
            teams = teamRepository.findAll();
        }

        return teams.stream().map(TeamResult::from).toList();
    }

    private SportType parseSportType(String sportType) {
        if (sportType == null) {
            return null;
        }
        try {
            return SportType.valueOf(sportType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public TeamResult getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .map(TeamResult::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }
}
