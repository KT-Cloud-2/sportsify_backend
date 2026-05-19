package com.sportsify.game.domain;

import com.sportsify.game.domain.model.*;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GameTest {

    @Test
    @DisplayName("Game.create()로 경기를 생성할 수 있다.")
    void createGame() {
        Stadium stadium = mock(Stadium.class);
        Team homeTeam = mock(Team.class);
        Team awayTeam = mock(Team.class);
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);
        LocalDateTime saleStartAt = LocalDateTime.of(2025, 4, 25, 10, 0);
        LocalDateTime saleEndAt = LocalDateTime.of(2025, 5, 1, 18, 0);

        Game game = Game.create(
                stadium, homeTeam, awayTeam, SportType.BASEBALL,
                startAt, 200, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 6,
                saleStartAt, saleEndAt
        );

        assertThat(game.getStadium()).isEqualTo(stadium);
        assertThat(game.getHomeTeam()).isEqualTo(homeTeam);
        assertThat(game.getAwayTeam()).isEqualTo(awayTeam);
        assertThat(game.getSportType()).isEqualTo(SportType.BASEBALL);
        assertThat(game.getStartAt()).isEqualTo(startAt);
        assertThat(game.getDurationMinutes()).isEqualTo(200);
        assertThat(game.getStatus()).isEqualTo(GameStatus.SCHEDULED);
        assertThat(game.getDayType()).isEqualTo(DayType.WEEKDAY);
        assertThat(game.getGameGrade()).isEqualTo(GameGrade.NORMAL);
        assertThat(game.getMaxTicketPerUser()).isEqualTo(6);
        assertThat(game.getSaleStartAt()).isEqualTo(saleStartAt);
        assertThat(game.getSaleEndAt()).isEqualTo(saleEndAt);
    }

    @Test
    @DisplayName("durationMinutes가 null이면 기본값 180이 설정된다.")
    void createGame_defaultDurationMinutes() {
        Stadium stadium = mock(Stadium.class);
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);

        Game game = Game.create(
                stadium, null, null, SportType.BASEBALL,
                startAt, null, GameStatus.SCHEDULED,
                DayType.WEEKEND, GameGrade.NORMAL, null,
                null, null
        );

        assertThat(game.getDurationMinutes()).isEqualTo(180);
    }

    @Test
    @DisplayName("maxTicketPerUser가 null이면 기본값 4가 설정된다.")
    void createGame_defaultMaxTicketPerUser() {
        Stadium stadium = mock(Stadium.class);
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);

        Game game = Game.create(
                stadium, null, null, SportType.BASEBALL,
                startAt, 180, GameStatus.SCHEDULED,
                DayType.WEEKEND, GameGrade.NORMAL, null,
                null, null
        );

        assertThat(game.getMaxTicketPerUser()).isEqualTo(4);
    }

    @Test
    @DisplayName("homeTeam, awayTeam이 null이어도 생성된다.")
    void createGame_nullableTeams() {
        Stadium stadium = mock(Stadium.class);
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);

        Game game = Game.create(
                stadium, null, null, SportType.BASEBALL,
                startAt, 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4,
                null, null
        );

        assertThat(game.getHomeTeam()).isNull();
        assertThat(game.getAwayTeam()).isNull();
    }
}
