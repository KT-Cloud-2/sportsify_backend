package com.sportsify.game.application.scheduler;

import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSaleTaskScheduler {

    private final TaskScheduler gameTaskScheduler;
    private final GameRepository gameRepository;

    public void scheduleSaleStart(Long gameId, LocalDateTime saleStartAt) {
        Instant startInstant = saleStartAt.atZone(ZoneId.systemDefault()).toInstant();

        gameTaskScheduler.schedule(() -> openSale(gameId), startInstant);
        log.info("[GAME_SALE_TASK] 판매 시작 예약 - gameId: {}, saleStartAt: {}", gameId, saleStartAt);
    }

    public void scheduleSaleEnd(Long gameId, LocalDateTime saleEndAt) {
        Instant endInstant = saleEndAt.atZone(ZoneId.systemDefault()).toInstant();

        gameTaskScheduler.schedule(() -> closeSale(gameId), endInstant);
        log.info("[GAME_SALE_TASK] 판매 종료 예약 - gameId: {}, saleEndAt: {}", gameId, saleEndAt);
    }

    @Transactional
    public void openSale(Long gameId) {
        gameRepository.findById(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.SCHEDULED) {
                game.updateStatus(GameStatus.ON_SALE);
                log.info("[GAME_SALE_TASK] 경기 ON_SALE 전환 - gameId: {}", gameId);
            }
        });
    }

    @Transactional
    public void closeSale(Long gameId) {
        gameRepository.findById(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.ON_SALE) {
                game.updateStatus(GameStatus.SALE_CLOSED);
                log.info("[GAME_SALE_TASK] 경기 SALE_CLOSED 전환 - gameId: {}", gameId);
            }
        });
    }
}
