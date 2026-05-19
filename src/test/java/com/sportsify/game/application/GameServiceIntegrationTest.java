package com.sportsify.game.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.game.application.service.GameService;
import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.StadiumRepository;
import com.sportsify.game.presentation.dto.GameCreateRequestDto;
import com.sportsify.game.presentation.dto.GameCreateResponseDto;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.infrastructure.repository.TeamJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameServiceIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private TeamJpaRepository teamRepository;

    @AfterEach
    void tearDown() {
        gameRepository.deleteAll();
        teamRepository.deleteAll();
        stadiumRepository.deleteAll();
    }

    @Test
    @DisplayName("경기 생성 시 DB에 저장되고 응답이 올바르게 반환된다.")
    void createGame_success() {
        // given
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("잠실야구장")
                        .address("서울시 송파구")
                        .totalSeats(25000)
                        .build()
        );

        Team homeTeam = teamRepository.save(
                Team.createForTest("두산베어스", "두산", SportType.BASEBALL)
        );

        Team awayTeam = teamRepository.save(
                Team.createForTest("LG트윈스", "LG", SportType.BASEBALL)
        );

        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);
        LocalDateTime saleStartAt = LocalDateTime.of(2025, 4, 25, 10, 0);
        LocalDateTime saleEndAt = LocalDateTime.of(2025, 5, 1, 18, 0);

        GameCreateRequestDto request = new GameCreateRequestDto(
                stadium.getId(), homeTeam.getId(), awayTeam.getId(),
                SportType.BASEBALL, startAt, 200, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 6,
                saleStartAt, saleEndAt
        );

        // when
        GameCreateResponseDto response = gameService.createGame(request);

        // then
        assertThat(response.id()).isNotNull();
        assertThat(response.stadiumId()).isEqualTo(stadium.getId());
        assertThat(response.homeTeamId()).isEqualTo(homeTeam.getId());
        assertThat(response.awayTeamId()).isEqualTo(awayTeam.getId());
        assertThat(response.sportType()).isEqualTo(SportType.BASEBALL);
        assertThat(response.startAt()).isEqualTo(startAt);
        assertThat(response.durationMinutes()).isEqualTo(200);
        assertThat(response.status()).isEqualTo(GameStatus.SCHEDULED);
        assertThat(response.dayType()).isEqualTo(DayType.WEEKDAY);
        assertThat(response.gameGrade()).isEqualTo(GameGrade.NORMAL);
        assertThat(response.maxTicketPerUser()).isEqualTo(6);
        assertThat(response.saleStartAt()).isEqualTo(saleStartAt);
        assertThat(response.saleEndAt()).isEqualTo(saleEndAt);
        assertThat(response.createdAt()).isNotNull();

        // DB 검증
        Game savedGame = gameRepository.findById(response.id()).orElseThrow();
        assertThat(savedGame.getStadium().getId()).isEqualTo(stadium.getId());
        assertThat(savedGame.getStatus()).isEqualTo(GameStatus.SCHEDULED);
    }

    @Test
    @DisplayName("팀 없이 경기 생성이 가능하다.")
    void createGame_withoutTeams() {
        // given
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("잠실야구장")
                        .address("서울시 송파구")
                        .totalSeats(25000)
                        .build()
        );

        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);

        GameCreateRequestDto request = new GameCreateRequestDto(
                stadium.getId(), null, null,
                SportType.BASEBALL, startAt, null, GameStatus.SCHEDULED,
                DayType.WEEKEND, GameGrade.NORMAL, null,
                null, null
        );

        // when
        GameCreateResponseDto response = gameService.createGame(request);

        // then
        assertThat(response.id()).isNotNull();
        assertThat(response.homeTeamId()).isNull();
        assertThat(response.awayTeamId()).isNull();
        assertThat(response.durationMinutes()).isEqualTo(180); // 기본값
        assertThat(response.maxTicketPerUser()).isEqualTo(4); // 기본값
    }

    @Test
    @DisplayName("존재하지 않는 경기장 ID로 생성 시 예외가 발생한다.")
    void createGame_stadiumNotFound() {
        // given
        GameCreateRequestDto request = new GameCreateRequestDto(
                9999L, null, null,
                SportType.BASEBALL, LocalDateTime.now().plusDays(7),
                180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        // when & then
        assertThatThrownBy(() -> gameService.createGame(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 홈팀 ID로 생성 시 예외가 발생한다.")
    void createGame_homeTeamNotFound() {
        // given
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("잠실야구장")
                        .address("서울시 송파구")
                        .totalSeats(25000)
                        .build()
        );

        GameCreateRequestDto request = new GameCreateRequestDto(
                stadium.getId(), 9999L, null,
                SportType.BASEBALL, LocalDateTime.now().plusDays(7),
                180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        // when & then
        assertThatThrownBy(() -> gameService.createGame(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("존재하지 않는 원정팀 ID로 생성 시 예외가 발생한다.")
    void createGame_awayTeamNotFound() {
        // given
        Stadium stadium = stadiumRepository.save(
                Stadium.builder()
                        .name("잠실야구장")
                        .address("서울시 송파구")
                        .totalSeats(25000)
                        .build()
        );

        Team homeTeam = teamRepository.save(
                Team.createForTest("두산베어스", "두산", SportType.BASEBALL)
        );

        GameCreateRequestDto request = new GameCreateRequestDto(
                stadium.getId(), homeTeam.getId(), 9999L,
                SportType.BASEBALL, LocalDateTime.now().plusDays(7),
                180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        // when & then
        assertThatThrownBy(() -> gameService.createGame(request))
                .isInstanceOf(BusinessException.class);
    }
}
