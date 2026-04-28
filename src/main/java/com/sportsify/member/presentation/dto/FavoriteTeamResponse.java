package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.FavoriteTeamResult;

public record FavoriteTeamResponse(
        Long favoriteTeamId,
        Long teamId,
        String teamName,
        String shortName,
        String sportType,
        int priority
) {
    public static FavoriteTeamResponse from(FavoriteTeamResult result) {
        return new FavoriteTeamResponse(
                result.favoriteTeamId(),
                result.teamId(),
                result.teamName(),
                result.shortName(),
                result.sportType(),
                result.priority()
        );
    }
}
