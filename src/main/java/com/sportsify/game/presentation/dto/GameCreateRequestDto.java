package com.sportsify.game.presentation.dto;

import com.sportsify.game.domain.model.DayType;
import com.sportsify.game.domain.model.GameGrade;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.team.domain.model.SportType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record GameCreateRequestDto(
        @NotNull(message = "경기장 ID는 필수입니다.")
        Long stadiumId,

        Long homeTeamId,

        Long awayTeamId,

        SportType sportType,

        @NotNull(message = "경기 시작 시간은 필수입니다.")
        LocalDateTime startAt,

        Integer durationMinutes,

        @NotNull(message = "경기 상태는 필수입니다.")
        GameStatus status,

        DayType dayType,

        GameGrade gameGrade,

        Integer maxTicketPerUser,

        LocalDateTime saleStartAt,

        LocalDateTime saleEndAt
) {
}
