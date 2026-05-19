package com.sportsify.game.application.scheduler;

import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.TicketOpenPayload;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameStatusUpdater {

    private final GameRepository gameRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    @Transactional
    public void openSale(Long gameId) {
        gameRepository.findById(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.SCHEDULED) {
                game.updateStatus(GameStatus.ON_SALE);

                notificationEventPublisher.publish(
                        NotificationEventType.TICKET_OPEN,
                        new TicketOpenPayload(game.getId(), game.getHomeTeamName(), game.getAwayTeamName(),
                                game.getSaleStartAt(), game.getSaleEndAt()));

                log.info("[GAME_STATUS] ON_SALE 전환 - gameId: {}", gameId);
            }
        });
    }

    @Transactional
    public void closeSale(Long gameId) {
        gameRepository.findById(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.ON_SALE) {
                game.updateStatus(GameStatus.SALE_CLOSED);
                log.info("[GAME_STATUS] SALE_CLOSED 전환 - gameId: {}", gameId);
            }
        });
    }
}
