package com.sportsify.game.presentation.dto;

import com.sportsify.game.application.dto.TeamInfo;
import com.sportsify.game.application.dto.TeamSide;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameGrade;

import java.time.LocalDateTime;
import java.util.List;

public record GameListResponseDto(
        Long gameId,
        String sportType,
        List<TeamInfo> teams,
        LocalDateTime gameTime,
        String stadium,
        String status,
        Integer totalSeats,
        Integer availableSeats,
        Boolean isRivalMatch
) {

    public static GameListResponseDto from(Game game, Integer availableSeats) {
        List<TeamInfo> teams = List.of(
                TeamInfo.of(game.getHomeTeam(), TeamSide.HOME),
                TeamInfo.of(game.getAwayTeam(), TeamSide.AWAY)
        );

        return new GameListResponseDto(
                game.getId(),
                game.getSportType().name(),
                teams,
                game.getStartAt(),
                game.getStadium().getName(),
                game.getStatus().name(),
                game.getStadium().getTotalSeats(),
                availableSeats,
                game.getGameGrade() == GameGrade.RIVAL
        );
    }

}