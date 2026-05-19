package com.sportsify.game.application.scheduler;

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

    @Transactional
    public void openSale(Long gameId) {
        gameRepository.findById(gameId).ifPresent(game -> {
            if (game.getStatus() == GameStatus.SCHEDULED) {
                game.updateStatus(GameStatus.ON_SALE);
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
