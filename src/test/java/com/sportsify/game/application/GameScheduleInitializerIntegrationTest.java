package com.sportsify.game.application;

import com.sportsify.game.application.scheduler.GameScheduleInitializer;
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

class GameScheduleInitializerIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private GameScheduleInitializer gameScheduleInitializer;

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
    @DisplayName("서버 시작 시 saleStartAt이 지난 SCHEDULED 경기가 ON_SALE로 보정된다.")
    void init_compensatesMissedOnSale() {
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
                        .saleStartAt(LocalDateTime.now().minusHours(1))
                        .saleEndAt(LocalDateTime.now().plusDays(6))
                        .build()
        );

        gameScheduleInitializer.init();

        Game updated = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GameStatus.ON_SALE);
    }

    @Test
    @DisplayName("서버 시작 시 saleEndAt이 지난 ON_SALE 경기가 SALE_CLOSED로 보정된다.")
    void init_compensatesMissedSaleClosed() {
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
                        .saleStartAt(LocalDateTime.now().minusDays(3))
                        .saleEndAt(LocalDateTime.now().minusHours(1))
                        .build()
        );

        gameScheduleInitializer.init();

        Game updated = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(GameStatus.SALE_CLOSED);
    }

    @Test
    @DisplayName("서버 시작 시 saleStartAt이 미래인 경기는 상태가 변경되지 않는다.")
    void init_futureGame_staysScheduled() {
        Stadium stadium = stadiumRepository.save(
                Stadium.builder().name("잠실").address("서울").totalSeats(25000).build()
        );

        Game game = gameRepository.save(
                Game.builder()
                        .stadium(stadium)
                        .sportType(SportType.BASEBALL)
                        .startAt(LocalDateTime.now().plusDays(14))
                        .status(GameStatus.SCHEDULED)
                        .dayType(DayType.WEEKDAY)
                        .gameGrade(GameGrade.NORMAL)
                        .saleStartAt(LocalDateTime.now().plusDays(7))
                        .saleEndAt(LocalDateTime.now().plusDays(13))
                        .build()
        );

        gameScheduleInitializer.init();

        Game unchanged = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(GameStatus.SCHEDULED);
    }
}
