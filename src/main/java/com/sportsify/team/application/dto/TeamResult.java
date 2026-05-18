package com.sportsify.team.application.dto;

import com.sportsify.team.domain.model.Team;

public record TeamResult(
        Long teamId,
        String name,
        String shortName,
        String sportType,
        String logoUrl,
        boolean active
) {
    public static TeamResult from(Team team) {
        return new TeamResult(
                team.getId(),
                team.getName(),
                team.getShortName(),
                team.getSportType().name(),
                team.getLogoUrl(),
                team.isActive()
        );
    }
}
