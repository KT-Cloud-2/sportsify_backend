package com.sportsify.game.application;

import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.TicketOpenPayload;
import com.sportsify.game.application.scheduler.GameStatusUpdater;
import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.ticketing.application.scheduler.OrderExpirationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameStatusUpdaterTest {

    @InjectMocks
    private GameStatusUpdater gameStatusUpdater;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private OrderExpirationScheduler orderExpirationScheduler;

    @Mock
    private TaskScheduler gameTaskScheduler;

    @Test
    @DisplayName("openSale 호출 시 SCHEDULED 상태인 경기가 ON_SALE로 변경된다.")
    void openSale_changesStatusToOnSale() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.SCHEDULED);
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));

        gameStatusUpdater.openSale(1L);

        verify(orderExpirationScheduler).onSaleStarted();
        verify(game).updateStatus(GameStatus.ON_SALE);
        verify(notificationEventPublisher).publish(eq(NotificationEventType.TICKET_OPEN), any(TicketOpenPayload.class));
    }

    @Test
    @DisplayName("openSale 호출 시 SCHEDULED가 아닌 경기는 변경하지 않는다.")
    void openSale_ignoresNonScheduledGame() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.ON_SALE);
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));

        gameStatusUpdater.openSale(1L);

        verify(game, never()).updateStatus(any());
    }

    @Test
    @DisplayName("openSale 호출 시 경기가 없으면 무시한다.")
    void openSale_gameNotFound() {
        when(gameRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        gameStatusUpdater.openSale(999L);

        verify(gameRepository).findByIdForUpdate(999L);
    }

    @Test
    @DisplayName("closeSale 호출 시 ON_SALE 상태인 경기가 SALE_CLOSED로 변경된다.")
    void closeSale_changesStatusToSaleClosed() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.ON_SALE);
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));

        gameStatusUpdater.closeSale(1L);

        verify(orderExpirationScheduler).onSaleEnded();
        verify(gameTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(game).updateStatus(GameStatus.SALE_CLOSED);
    }

    @Test
    @DisplayName("closeSale 호출 시 ON_SALE이 아닌 경기는 변경하지 않는다.")
    void closeSale_ignoresNonOnSaleGame() {
        Game game = mock(Game.class);
        when(game.getStatus()).thenReturn(GameStatus.SCHEDULED);
        when(gameRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(game));

        gameStatusUpdater.closeSale(1L);

        verify(game, never()).updateStatus(any());
    }

    @Test
    @DisplayName("closeSale 호출 시 경기가 없으면 무시한다.")
    void closeSale_gameNotFound() {
        when(gameRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        gameStatusUpdater.closeSale(999L);

        verify(gameRepository).findByIdForUpdate(999L);
    }
}
