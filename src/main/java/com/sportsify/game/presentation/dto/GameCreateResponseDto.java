package com.sportsify.game.presentation.dto;

import com.sportsify.game.domain.model.DayType;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameGrade;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.team.domain.model.SportType;

import java.time.LocalDateTime;

public record GameCreateResponseDto(
        Long id,
        Long stadiumId,
        Long homeTeamId,
        Long awayTeamId,
        SportType sportType,
        LocalDateTime startAt,
        Integer durationMinutes,
        GameStatus status,
        DayType dayType,
        GameGrade gameGrade,
        Integer maxTicketPerUser,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt,
        LocalDateTime createdAt
) {
    public static GameCreateResponseDto from(Game game) {
        return new GameCreateResponseDto(
                game.getId(),
                game.getStadium().getId(),
                game.getHomeTeam() != null ? game.getHomeTeam().getId() : null,
                game.getAwayTeam() != null ? game.getAwayTeam().getId() : null,
                game.getSportType(),
                game.getStartAt(),
                game.getDurationMinutes(),
                game.getStatus(),
                game.getDayType(),
                game.getGameGrade(),
                game.getMaxTicketPerUser(),
                game.getSaleStartAt(),
                game.getSaleEndAt(),
                game.getCreatedAt()
        );
    }
}
