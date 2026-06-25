package com.sportsify.game.application.scheduler;

import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.TicketOpenPayload;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.ticketing.application.scheduler.OrderMaintenanceScheduler;
import com.sportsify.ticketing.application.service.OrderService;
import com.sportsify.ticketing.domain.model.OrderConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameStatusUpdater {

    private final GameRepository gameRepository;
    private final OrderService orderService;
    private final TaskScheduler gameTaskScheduler;
    private final NotificationEventPublisher notificationEventPublisher;
    private final OrderMaintenanceScheduler orderMaintenanceScheduler;

    public void openSale(Long gameId) {
        gameRepository.findByIdForUpdate(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.SCHEDULED) {
                game.updateStatus(GameStatus.ON_SALE);
                orderMaintenanceScheduler.onSaleStarted();

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
        gameRepository.findByIdForUpdate(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.ON_SALE) {
                game.updateStatus(GameStatus.SALE_CLOSED);
                orderMaintenanceScheduler.onSaleEnded();

                gameTaskScheduler.schedule(() -> {
                    orderService.expireUnpaidOrdersBulk();
                    orderService.cancelFailedPaymentOrdersBulk();
                }, Instant.now().plusSeconds(OrderConstants.CLEANUP_DELAY_MINUTES * 60L));

                log.info("[GAME_STATUS] SALE_CLOSED 전환 - gameId: {}", gameId);
            }
        });
    }
}
