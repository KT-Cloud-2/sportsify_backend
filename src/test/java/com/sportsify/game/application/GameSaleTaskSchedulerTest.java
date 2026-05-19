package com.sportsify.game.application;

import com.sportsify.game.application.scheduler.GameSaleTaskScheduler;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSaleTaskSchedulerTest {

    @InjectMocks
    private GameSaleTaskScheduler gameSaleTaskScheduler;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private GameRepository gameRepository;

    @Test
    @DisplayName("scheduleSaleStart 호출 시 TaskScheduler에 정확한 시각으로 등록된다.")
    void scheduleSaleStart_registersCorrectInstant() {
        LocalDateTime saleStartAt = LocalDateTime.of(2025, 5, 1, 10, 0);
        Instant expectedInstant = saleStartAt.atZone(ZoneId.systemDefault()).toInstant();

        gameSaleTaskScheduler.scheduleSaleStart(1L, saleStartAt);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isEqualTo(expectedInstant);
    }

    @Test
    @DisplayName("scheduleSaleEnd 호출 시 TaskScheduler에 정확한 시각으로 등록된다.")
    void scheduleSaleEnd_registersCorrectInstant() {
        LocalDateTime saleEndAt = LocalDateTime.of(2025, 5, 1, 18, 0);
        Instant expectedInstant = saleEndAt.atZone(ZoneId.systemDefault()).toInstant();

        gameSaleTaskScheduler.scheduleSaleEnd(1L, saleEndAt);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isEqualTo(expectedInstant);
    }

    @Test
    @DisplayName("openSale 호출 시 SCHEDULED 상태인 경기가 ON_SALE로 변경된다.")
    void openSale_changesStatusToOnSale() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.SCHEDULED);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        gameSaleTaskScheduler.openSale(1L);

        verify(game).updateStatus(GameStatus.ON_SALE);
    }

    @Test
    @DisplayName("openSale 호출 시 SCHEDULED가 아닌 경기는 변경하지 않는다.")
    void openSale_ignoresNonScheduledGame() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.ON_SALE);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        gameSaleTaskScheduler.openSale(1L);

        verify(game, never()).updateStatus(any());
    }

    @Test
    @DisplayName("closeSale 호출 시 ON_SALE 상태인 경기가 SALE_CLOSED로 변경된다.")
    void closeSale_changesStatusToSaleClosed() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.ON_SALE);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        gameSaleTaskScheduler.closeSale(1L);

        verify(game).updateStatus(GameStatus.SALE_CLOSED);
    }

    @Test
    @DisplayName("closeSale 호출 시 ON_SALE이 아닌 경기는 변경하지 않는다.")
    void closeSale_ignoresNonOnSaleGame() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.SCHEDULED);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        gameSaleTaskScheduler.closeSale(1L);

        verify(game, never()).updateStatus(any());
    }

    @Test
    @DisplayName("openSale 호출 시 경기가 없으면 무시한다.")
    void openSale_gameNotFound() {
        when(gameRepository.findById(999L)).thenReturn(Optional.empty());

        gameSaleTaskScheduler.openSale(999L);

        verify(gameRepository).findById(999L);
    }
}
