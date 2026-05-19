package com.sportsify.game.application;

import com.sportsify.game.application.scheduler.GameSaleTaskScheduler;
import com.sportsify.game.application.scheduler.GameScheduleInitializer;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameScheduleInitializerTest {

    @InjectMocks
    private GameScheduleInitializer gameScheduleInitializer;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameSaleTaskScheduler gameSaleTaskScheduler;

    @Test
    @DisplayName("서버 시작 시 saleStartAt이 지난 SCHEDULED 경기를 즉시 ON_SALE로 전환한다.")
    void init_compensatesMissedGames() {
        Game missedGame = mock(Game.class);
        when(gameRepository.findGamesReadyForSale(any())).thenReturn(List.of(missedGame));
        when(gameRepository.findGamesToCloseSale(any())).thenReturn(List.of());
        when(gameRepository.findUpcomingScheduledGames(any())).thenReturn(List.of());
        when(gameRepository.findOnSaleGamesWithFutureSaleEnd(any())).thenReturn(List.of());

        gameScheduleInitializer.init();

        verify(missedGame).updateStatus(GameStatus.ON_SALE);
    }

    @Test
    @DisplayName("서버 시작 시 saleEndAt이 지난 ON_SALE 경기를 즉시 SALE_CLOSED로 전환한다.")
    void init_compensatesMissedCloseGames() {
        Game missedCloseGame = mock(Game.class);
        when(gameRepository.findGamesReadyForSale(any())).thenReturn(List.of());
        when(gameRepository.findGamesToCloseSale(any())).thenReturn(List.of(missedCloseGame));
        when(gameRepository.findUpcomingScheduledGames(any())).thenReturn(List.of());
        when(gameRepository.findOnSaleGamesWithFutureSaleEnd(any())).thenReturn(List.of());

        gameScheduleInitializer.init();

        verify(missedCloseGame).updateStatus(GameStatus.SALE_CLOSED);
    }

    @Test
    @DisplayName("서버 시작 시 미래 경기를 TaskScheduler에 등록한다.")
    void init_registersUpcomingGames() {
        Game upcomingGame = mock(Game.class);
        LocalDateTime saleStartAt = LocalDateTime.now().plusDays(1);
        LocalDateTime saleEndAt = LocalDateTime.now().plusDays(5);

        when(upcomingGame.getId()).thenReturn(1L);
        when(upcomingGame.getSaleStartAt()).thenReturn(saleStartAt);
        when(upcomingGame.getSaleEndAt()).thenReturn(saleEndAt);

        when(gameRepository.findGamesReadyForSale(any())).thenReturn(List.of());
        when(gameRepository.findGamesToCloseSale(any())).thenReturn(List.of());
        when(gameRepository.findUpcomingScheduledGames(any())).thenReturn(List.of(upcomingGame));
        when(gameRepository.findOnSaleGamesWithFutureSaleEnd(any())).thenReturn(List.of());

        gameScheduleInitializer.init();

        verify(gameSaleTaskScheduler).scheduleSaleStart(1L, saleStartAt);
        verify(gameSaleTaskScheduler).scheduleSaleEnd(1L, saleEndAt);
    }

    @Test
    @DisplayName("서버 시작 시 ON_SALE 경기의 판매 종료 예약을 등록한다.")
    void init_registersOnSaleGameEndSchedule() {
        Game onSaleGame = mock(Game.class);
        LocalDateTime saleEndAt = LocalDateTime.now().plusDays(2);

        when(onSaleGame.getId()).thenReturn(2L);
        when(onSaleGame.getSaleEndAt()).thenReturn(saleEndAt);

        when(gameRepository.findGamesReadyForSale(any())).thenReturn(List.of());
        when(gameRepository.findGamesToCloseSale(any())).thenReturn(List.of());
        when(gameRepository.findUpcomingScheduledGames(any())).thenReturn(List.of());
        when(gameRepository.findOnSaleGamesWithFutureSaleEnd(any())).thenReturn(List.of(onSaleGame));

        gameScheduleInitializer.init();

        verify(gameSaleTaskScheduler).scheduleSaleEnd(2L, saleEndAt);
    }
}
