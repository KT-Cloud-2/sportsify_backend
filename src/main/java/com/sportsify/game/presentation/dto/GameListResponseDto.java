package com.sportsify.game.presentation.dto;

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
                new TeamInfo(
                        game.getHomeTeam().getId(),
                        game.getHomeTeam().getName(),
                        game.getHomeTeam().getShortName(),
                        TeamSide.HOME
                ),
                new TeamInfo(
                        game.getAwayTeam().getId(),
                        game.getAwayTeam().getName(),
                        game.getAwayTeam().getShortName(),
                        TeamSide.AWAY
                )
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

    public enum TeamSide {
        HOME, AWAY, NEUTRAL
    }

    public record TeamInfo(
            Long teamId,
            String name,
            String shortName,
            TeamSide side
    ) {
        public static TeamInfo of(Long teamId, String name, String shortName, TeamSide side) {
            return new TeamInfo(teamId, name, shortName, side);
        }
    }
}