package com.sportsify.game.application;

import com.sportsify.game.application.scheduler.GameSaleTaskScheduler;
import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.StadiumRepository;
import com.sportsify.support.RepositoryTestSupport;
import com.sportsify.team.domain.model.SportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GameSaleTaskSchedulerIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private GameSaleTaskScheduler gameSaleTaskScheduler;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private StadiumRepository stadiumRepository;

    @AfterEach
    void tearDown() {
        gameRepository.deleteAll();
        stadiumRepository.deleteAll();
    }

    @Test
    @DisplayName("openSale 호출 시 SCHEDULED 경기가 ON_SALE로 변경된다.")
    void openSale_success() {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder().name("잠실").address("서울").totalSeats(25000).build()
        );

        Game game = gameRepository.save(
                Game.builder()
                        .stadium(stadium)
                        .sportType(SportType.BASEBALL)
                        .startAt(LocalDateTime.now().plusDays(7))
                        .status(GameStatus.SCHEDULED)
                        .dayType(DayType.WEEKDAY)
                        .gameGrade(GameGrade.NORMAL)
                        .saleStartAt(LocalDateTime.now().minusMinutes(1))
                        .build()
        );

        gameSaleTaskScheduler.openSale(game.getId());

        Game updated = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GameStatus.ON_SALE);
    }

    @Test
    @DisplayName("closeSale 호출 시 ON_SALE 경기가 SALE_CLOSED로 변경된다.")
    void closeSale_success() {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder().name("잠실").address("서울").totalSeats(25000).build()
        );

        Game game = gameRepository.save(
                Game.builder()
                        .stadium(stadium)
                        .sportType(SportType.BASEBALL)
                        .startAt(LocalDateTime.now().plusDays(7))
                        .status(GameStatus.ON_SALE)
                        .dayType(DayType.WEEKDAY)
                        .gameGrade(GameGrade.NORMAL)
                        .saleEndAt(LocalDateTime.now().minusMinutes(1))
                        .build()
        );

        gameSaleTaskScheduler.closeSale(game.getId());

        Game updated = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GameStatus.SALE_CLOSED);
    }

    @Test
    @DisplayName("openSale 호출 시 이미 ON_SALE인 경기는 변경되지 않는다.")
    void openSale_alreadyOnSale_noChange() {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder().name("잠실").address("서울").totalSeats(25000).build()
        );

        Game game = gameRepository.save(
                Game.builder()
                        .stadium(stadium)
                        .sportType(SportType.BASEBALL)
                        .startAt(LocalDateTime.now().plusDays(7))
                        .status(GameStatus.ON_SALE)
                        .dayType(DayType.WEEKDAY)
                        .gameGrade(GameGrade.NORMAL)
                        .build()
        );

        gameSaleTaskScheduler.openSale(game.getId());

        Game unchanged = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(GameStatus.ON_SALE);
    }

    @Test
    @DisplayName("존재하지 않는 gameId로 호출 시 예외 없이 무시된다.")
    void openSale_gameNotFound_noException() {
        gameSaleTaskScheduler.openSale(9999L);
        // 예외 발생하지 않으면 성공
    }
}
