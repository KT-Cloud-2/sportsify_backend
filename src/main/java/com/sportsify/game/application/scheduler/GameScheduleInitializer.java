package com.sportsify.game.application.scheduler;

import com.sportsify.game.domain.model.Game;
import com.sportsify.game.domain.model.GameStatus;
import com.sportsify.game.domain.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameScheduleInitializer {

    private final GameRepository gameRepository;
    private final GameSaleTaskScheduler gameSaleTaskScheduler;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        LocalDateTime now = LocalDateTime.now();

        // 1. saleStartAt이 이미 지났는데 SCHEDULED인 경기 → 즉시 ON_SALE
        List<Game> missedGames = gameRepository.findGamesReadyForSale(now);
        for (Game game : missedGames) {
            game.updateStatus(GameStatus.ON_SALE);
        }
        log.info("[GAME_SCHEDULE_INIT] 서버 시작 보정 - {}건 즉시 ON_SALE 전환", missedGames.size());

        // 2. saleEndAt이 이미 지났는데 ON_SALE인 경기 → 즉시 SALE_CLOSED
        List<Game> missedCloseGames = gameRepository.findGamesToCloseSale(now);
        for (Game game : missedCloseGames) {
            game.updateStatus(GameStatus.SALE_CLOSED);
        }
        log.info("[GAME_SCHEDULE_INIT] 서버 시작 보정 - {}건 즉시 SALE_CLOSED 전환", missedCloseGames.size());

        // 3. 아직 saleStartAt이 안 된 경기 → TaskScheduler 등록
        List<Game> upcomingGames = gameRepository.findUpcomingScheduledGames(now);
        for (Game game : upcomingGames) {
            gameSaleTaskScheduler.scheduleSaleStart(game.getId(), game.getSaleStartAt());

            if (game.getSaleEndAt() != null) {
                gameSaleTaskScheduler.scheduleSaleEnd(game.getId(), game.getSaleEndAt());
            }
        }
        log.info("[GAME_SCHEDULE_INIT] TaskScheduler 등록 - {}건 예약 완료", upcomingGames.size());

        // 4. ON_SALE 상태이면서 saleEndAt이 아직인 경기 → 종료 예약만 등록
        List<Game> onSaleGames = gameRepository.findOnSaleGamesWithFutureSaleEnd(now);
        for (Game game : onSaleGames) {
            gameSaleTaskScheduler.scheduleSaleEnd(game.getId(), game.getSaleEndAt());
        }
        log.info("[GAME_SCHEDULE_INIT] 판매 종료 예약 등록 - {}건", onSaleGames.size());
    }
}
