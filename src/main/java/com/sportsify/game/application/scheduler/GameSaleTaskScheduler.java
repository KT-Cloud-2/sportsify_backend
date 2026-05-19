package com.sportsify.game.application.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSaleTaskScheduler {

    private final TaskScheduler gameTaskScheduler;
    private final GameStatusUpdater gameStatusUpdater;

    public void scheduleSaleStart(Long gameId, LocalDateTime saleStartAt) {
        Instant startInstant = saleStartAt.atZone(ZoneId.systemDefault()).toInstant();

        try {
            gameTaskScheduler.schedule(() -> gameStatusUpdater.openSale(gameId), startInstant);
            log.info("[GAME_SALE_TASK] 판매 시작 예약 - gameId: {}, saleStartAt: {}", gameId, saleStartAt);
        } catch (TaskRejectedException ex) {
            log.error("[GAME_SALE_TASK] 판매 시작 예약 거절 - gameId: {}, saleStartAt: {}", gameId, saleStartAt, ex);
        }
    }

    public void scheduleSaleEnd(Long gameId, LocalDateTime saleEndAt) {
        Instant endInstant = saleEndAt.atZone(ZoneId.systemDefault()).toInstant();

        try {
            gameTaskScheduler.schedule(() -> gameStatusUpdater.closeSale(gameId), endInstant);
            log.info("[GAME_SALE_TASK] 판매 종료 예약 - gameId: {}, saleEndAt: {}", gameId, saleEndAt);
        } catch (TaskRejectedException ex) {
            log.error("[GAME_SALE_TASK] 판매 종료 예약 거절 - gameId: {}, saleEndAt: {}", gameId, saleEndAt, ex);
        }
    }

}
