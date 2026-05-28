package com.sportsify.ticketing.presentation;

import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.support.ApiTestSupport;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationApiIntegrationTest extends ApiTestSupport {
    private Long memberId;
    private Game game;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private GameRepository gameRepository;

    @BeforeEach
    void beforeEach() {
        memberId = fixture.createMember("t1@test.com", "n1").getId();
        game = fixture.createGame();
    }

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("잘못된 회원이 요청 시, 404 Not Found 에러를 반환한다.")
    void exception_memberNotFound() throws Exception {

        List<Long> seats = fixture.createGameSeatsWithCount(game, 1);

        String requestBody = """
                {"gameId": %d, "seatIds": %s}
                """.formatted(game.getId(), seats);

        mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(-1L, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 게임에 예매를 요청할 경우, 404 Not Found 에러를 반환한다.")
    void exception_gameNotFound() throws Exception {

        List<Long> seats = fixture.createGameSeatsWithCount(game, 1);

        postAPIwithBody(-1L, seats)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("게임이 예매 가능한 상태가 아니라면, 422 에러를 반환한다.")
    void exception_gameIsNotOnSale() throws Exception {

        game.updateStatus(GameStatus.CANCELLED);
        gameRepository.save(game);

        List<Long> seats = fixture.createGameSeatsWithCount(game, 1);

        postAPIwithBody(game.getId(), seats)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("GAME_NOT_ON_SALE"));
    }


    @Test
    @DisplayName("예매 가능한 좌석수를 초과하면, 422 에러를 반환한다.")
    void exception_exceedTicketMax() throws Exception {

        List<Long> seats = fixture.createGameSeatsWithCount(game, 5);

        postAPIwithBody(game.getId(), seats)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("TICKET_LIMIT_EXCEEDED"));
    }


    @Test
    @DisplayName("좌석이 중복되어 신청되면, 400 Bad Request 에러를 반환한다.")
    void exception_seatIsDuplicated() throws Exception {

        Long seats = fixture.createGameSeatsWithCount(game, 1).getFirst();
        List<Long> duplicatedSeats = List.of(seats, seats);

        postAPIwithBody(game.getId(), duplicatedSeats)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_DUPLICATED"));
    }

    @Test
    @DisplayName("동일한 게임의 좌석이 요청되지 않으면, 400 Bad Request 에러를 반환한다.")
    void exception_mismatchGamesInRequestSeats() throws Exception {

        Game game2 = gameRepository.save(Game.builder()
                .stadium(game.getStadium())
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .sportType(SportType.BASEBALL)
                .startAt(LocalDateTime.now().plusDays(7))
                .status(GameStatus.SCHEDULED)
                .dayType(DayType.WEEKDAY)
                .gameGrade(GameGrade.NORMAL)
                .build());

        List<Long> seats = List.of(fixture.createGameSeatsWithCount(game, 1).getFirst(),
                fixture.createGameSeatsWithCount(game2, 1).getFirst());

        postAPIwithBody(game.getId(), seats)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GAME_MISMATCH"));
    }

    @Test
    @DisplayName("이미 선점된 좌석을 포함하면, 409 Conflict 에러를 반환한다.")
    void exception_someSeatsAlreadyReserved() throws Exception {

        List<Long> seats = fixture.createGameSeatsWithCount(game, 2);

        GameSeat gameSeat = gameSeatRepository.findById(seats.getFirst()).orElseThrow(RuntimeException::new);
        gameSeat.updateSeatStatus(SeatStatus.RESERVED);
        gameSeatRepository.save(gameSeat);

        postAPIwithBody(game.getId(), seats)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
    }

    @Test
    @DisplayName("좌석 선점 성공시, 200 Ok 성공을 반환한다.")
    void success() throws Exception {

        List<Long> seats = fixture.createGameSeatsWithCount(game, 1);

        postAPIwithBody(game.getId(), seats)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.gameId").value(game.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(fixture.TICKET_PRICE))
                .andExpect(jsonPath("$.seats.length()").value(1))
                .andExpect(jsonPath("$.seats[0].seatId").value(seats.getFirst()));
    }


    private ResultActions postAPIwithBody(Long gameId, List<Long> seats) throws Exception {
        String body = """
                {"gameId": %d, "seatIds": %s}
                """.formatted(gameId, seats);

        return mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(memberId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());
    }

}
