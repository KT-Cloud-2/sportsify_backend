package com.sportsify.game.application.dto;


import com.sportsify.team.domain.model.Team;

public record TeamInfo(
        Long teamId,
        String name,
        String shortName,
        TeamSide side
) {
    public static TeamInfo of(Team team, TeamSide side) {
        return new TeamInfo(team.getId(), team.getName(), team.getShortName(), side);
    }
}