package com.sportsify.member.application.dto;

import com.sportsify.member.domain.model.MemberFavoriteTeam;

public record FavoriteTeamResult(
        Long favoriteTeamId,
        Long teamId,
        String teamName,
        String shortName,
        String sportType,
        int priority
) {
    public static FavoriteTeamResult from(MemberFavoriteTeam favoriteTeam) {
        return new FavoriteTeamResult(
                favoriteTeam.getId(),
                favoriteTeam.getTeam().getId(),
                favoriteTeam.getTeam().getName(),
                favoriteTeam.getTeam().getShortName(),
                favoriteTeam.getTeam().getSportType().name(),
                favoriteTeam.getPriority()
        );
    }
}
