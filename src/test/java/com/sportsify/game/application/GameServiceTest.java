package com.sportsify.game.application;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.GameStartPayload;
import com.sportsify.game.application.scheduler.GameSaleTaskScheduler;
import com.sportsify.game.application.service.GameService;
import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.StadiumRepository;
import com.sportsify.game.presentation.dto.GameCreateRequestDto;
import com.sportsify.game.presentation.dto.GameCreateResponseDto;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.team.domain.model.Team;
import com.sportsify.team.domain.repository.TeamRepository;
import com.sportsify.ticketing.application.scheduler.OrderExpirationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @InjectMocks
    private GameService gameService;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private StadiumRepository stadiumRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private GameSaleTaskScheduler gameSaleTaskScheduler;

    @Mock
    private OrderExpirationScheduler orderExpirationScheduler;


    @Test
    @DisplayName("경기 생성 성공")
    void createGame_success() {
        // given
        Stadium stadium = mock(Stadium.class);
        Team homeTeam = mock(Team.class);
        Team awayTeam = mock(Team.class);
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);

        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 1L, 2L, SportType.BASEBALL,
                startAt, 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4,
                startAt.minusDays(5), startAt.plusDays(5)
        );

        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(2L)).thenReturn(Optional.of(awayTeam));

        Game savedGame = mock(Game.class);
        when(savedGame.getSaleStartAt()).thenReturn(startAt.minusDays(5));
        when(savedGame.getSaleEndAt()).thenReturn(startAt.plusDays(5));
        when(savedGame.getId()).thenReturn(1L);
        when(savedGame.getStadium()).thenReturn(stadium);
        when(savedGame.getHomeTeam()).thenReturn(homeTeam);
        when(savedGame.getAwayTeam()).thenReturn(awayTeam);
        when(savedGame.getSportType()).thenReturn(SportType.BASEBALL);
        when(savedGame.getStartAt()).thenReturn(startAt);
        when(savedGame.getDurationMinutes()).thenReturn(180);
        when(savedGame.getStatus()).thenReturn(GameStatus.SCHEDULED);
        when(savedGame.getDayType()).thenReturn(DayType.WEEKDAY);
        when(savedGame.getGameGrade()).thenReturn(GameGrade.NORMAL);
        when(savedGame.getMaxTicketPerUser()).thenReturn(4);
        when(savedGame.hasSaleSchedule()).thenReturn(true);
        when(stadium.getId()).thenReturn(1L);
        when(homeTeam.getId()).thenReturn(1L);
        when(awayTeam.getId()).thenReturn(2L);

        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        // when
        GameCreateResponseDto response = gameService.createGame(request);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.stadiumId()).isEqualTo(1L);
        assertThat(response.homeTeamId()).isEqualTo(1L);
        assertThat(response.awayTeamId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(GameStatus.SCHEDULED);

        verify(gameSaleTaskScheduler).scheduleSaleStart(eq(1L), eq(startAt.minusDays(5)));
        verify(gameSaleTaskScheduler).scheduleSaleEnd(eq(1L), eq(startAt.plusDays(5)));

        verify(gameRepository).save(any(Game.class));
        verify(notificationEventPublisher).publish(eq(NotificationEventType.GAME_START), any(GameStartPayload.class));
    }

    @Test
    @DisplayName("존재하지 않는 경기장 ID로 생성 시 예외가 발생한다.")
    void createGame_stadiumNotFound() {
        // given
        GameCreateRequestDto request = new GameCreateRequestDto(
                999L, 1L, 2L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        when(stadiumRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameService.createGame(request))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STADIUM_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 홈팀 ID로 생성 시 예외가 발생한다.")
    void createGame_homeTeamNotFound() {
        // given
        Stadium stadium = mock(Stadium.class);

        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 999L, 2L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameService.createGame(request))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 원정팀 ID로 생성 시 예외가 발생한다.")
    void createGame_awayTeamNotFound() {
        // given
        Stadium stadium = mock(Stadium.class);
        Team homeTeam = mock(Team.class);

        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, 1L, 999L, SportType.BASEBALL,
                LocalDateTime.now().plusDays(7), 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gameService.createGame(request))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("팀 ID가 null이면 팀 조회 없이 경기가 생성된다.")
    void createGame_nullTeamIds() {
        // given
        Stadium stadium = mock(Stadium.class);
        LocalDateTime startAt = LocalDateTime.of(2025, 5, 1, 18, 30);

        GameCreateRequestDto request = new GameCreateRequestDto(
                1L, null, null, SportType.BASEBALL,
                startAt, 180, GameStatus.SCHEDULED,
                DayType.WEEKDAY, GameGrade.NORMAL, 4, null, null
        );

        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));

        Game savedGame = mock(Game.class);
        when(savedGame.getId()).thenReturn(1L);
        when(savedGame.getStadium()).thenReturn(stadium);
        when(savedGame.getHomeTeam()).thenReturn(null);
        when(savedGame.getAwayTeam()).thenReturn(null);
        when(savedGame.getSportType()).thenReturn(SportType.BASEBALL);
        when(savedGame.getStartAt()).thenReturn(startAt);
        when(savedGame.getDurationMinutes()).thenReturn(180);
        when(savedGame.getStatus()).thenReturn(GameStatus.SCHEDULED);
        when(savedGame.getDayType()).thenReturn(DayType.WEEKDAY);
        when(savedGame.getGameGrade()).thenReturn(GameGrade.NORMAL);
        when(savedGame.getMaxTicketPerUser()).thenReturn(4);
        when(stadium.getId()).thenReturn(1L);

        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        // when
        GameCreateResponseDto response = gameService.createGame(request);

        // then
        assertThat(response.homeTeamId()).isNull();
        assertThat(response.awayTeamId()).isNull();

        verify(teamRepository, never()).findById(any());
        verify(notificationEventPublisher).publish(eq(NotificationEventType.GAME_START), any(GameStartPayload.class));
    }
}
