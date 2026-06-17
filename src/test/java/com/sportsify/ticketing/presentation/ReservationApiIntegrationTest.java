package com.sportsify.ticketing.presentation;

import com.jayway.jsonpath.JsonPath;
import com.sportsify.game.domain.model.*;
import com.sportsify.game.domain.repository.GameRepository;
import com.sportsify.game.domain.repository.GameSeatRepository;
import com.sportsify.notification.infrastructure.publisher.RedisStreamNotificationEventPublisher;
import com.sportsify.support.ApiTestSupport;
import com.sportsify.team.domain.model.SportType;
import com.sportsify.ticketing.fixture.TicketingTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class 소ReservationApiIntegrationTest extends ApiTestSupport {
    private Long memberId;
    private Game game;

    @Autowired
    private TicketingTestFixture fixture;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private GameRepository gameRepository;

    @MockitoBean
    private RedisStreamNotificationEventPublisher redisStreamNotificationEventPublisher;

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

    @Test
    @DisplayName("동시 결제 확인 시 커넥션 풀 경합 상황에서도 200 반환")
    void confirmPayment_concurrent_shouldAllReturn200() throws Exception {
        int userCount = 20;
        List<String> tossOrderIds = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        List<Long> memberIds = new ArrayList<>();

        for (int i = 0; i < userCount; i++) {
            Long mid = fixture.createMember("concurrent" + i + "@test.com", "user" + i).getId();
            memberIds.add(mid);

            List<Long> seatIds = fixture.createGameSeatsWithCount(game, 1);

            String reservationResponse = mockMvc.perform(post("/api/seats/reservations")
                            .header("Authorization", bearerToken(mid, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"gameId": %d, "seatIds": %s}
                                    """.formatted(game.getId(), seatIds)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            Long orderId = JsonPath.parse(reservationResponse).read("$.orderId", Long.class);
            Integer amount = JsonPath.parse(reservationResponse).read("$.amount", Integer.class);
            amounts.add(amount);

            String paymentResponse = mockMvc.perform(post("/api/payments")
                            .header("Authorization", bearerToken(mid, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "orderId": %d,
                                        "matchId": %d,
                                        "seatId": %d,
                                        "amount": %d,
                                        "paymentMethod": "CARD",
                                        "idempotencyKey": "idem-concurrent-%d"
                                    }
                                    """.formatted(orderId, game.getId(), seatIds.get(0), amount, mid)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            tossOrderIds.add(JsonPath.parse(paymentResponse).read("$.tossOrderId", String.class));
        }

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < userCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await(); // 모든 스레드가 준비될 때까지 대기

                String confirmRequest = """
                        {
                            "paymentKey": "mock_pk_concurrent_%d",
                            "tossOrderId": "%s",
                            "amount": %d
                        }
                        """.formatted(idx, tossOrderIds.get(idx), amounts.get(idx));

                return mockMvc.perform(post("/api/payments/confirm")
                                .header("Authorization", bearerToken(memberIds.get(idx), "USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(confirmRequest))
                        .andReturn().getResponse().getStatus();
            }));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("결제 확인 시 이벤트 리스너 실패가 HTTP 응답에 영향을 주는지 확인")
    void confirmPayment_shouldReturn200_evenIfEventListenerFails() throws Exception {
        List<Long> seatIds = fixture.createGameSeatsWithCount(game, 1);

        String reservationRequest = """
                {"gameId": %d, "seatIds": %s}
                """.formatted(game.getId(), seatIds);

        String reservationResponse = mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", bearerToken(memberId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long orderId = JsonPath.parse(reservationResponse).read("$.orderId", Long.class);
        Integer amount = JsonPath.parse(reservationResponse).read("$.amount", Integer.class);

        String createPaymentRequest = """
                {
                    "orderId": %d,
                    "matchId": %d,
                    "seatId": %d,
                    "amount": %d,
                    "paymentMethod": "CARD",
                    "idempotencyKey": "test-idem-%d"
                }
                """.formatted(orderId, game.getId(), seatIds.get(0), amount, memberId);

        String paymentResponse = mockMvc.perform(post("/api/payments")
                        .header("Authorization", bearerToken(memberId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPaymentRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String tossOrderId = JsonPath.parse(paymentResponse).read("$.tossOrderId", String.class);

        String confirmRequest = """
                {
                    "paymentKey": "mock_pk_test",
                    "tossOrderId": "%s",
                    "amount": %d
                }
                """.formatted(tossOrderId, amount);

        mockMvc.perform(post("/api/payments/confirm")
                        .header("Authorization", bearerToken(memberId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
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
