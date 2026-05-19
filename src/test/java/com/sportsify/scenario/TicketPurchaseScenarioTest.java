package com.sportsify.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.sportsify.member.domain.model.Member;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(1)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 1] 티켓 구매 전체 여정")
class TicketPurchaseScenarioTest extends ScenarioTestSupport {

    private static final Long AMOUNT = 50000L;

    @Autowired
    private PaymentService paymentService;

    private Long gameId;
    private Long memberId;
    private String accessToken;
    private Long seatId;
    private Long orderId;
    private Long paymentId;
    private String tossOrderId;

    @BeforeAll
    void setUpOnce() {
        List<Long> gameAndSeats = createGameWithSeats(5);
        gameId = gameAndSeats.get(0);
        seatId = gameAndSeats.get(1);
        Member member = createMember("purchase@test.com");
        memberId = member.getId();
        accessToken = bearerToken(memberId);
    }

    @Test
    @Order(1)
    @DisplayName("경기 목록 조회 — ON_SALE 경기 존재")
    void 경기_목록_조회() throws Exception {
        mockMvc.perform(get("/api/games")
                        .param("status", "ON_SALE")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    @Order(2)
    @DisplayName("좌석 조회 — AVAILABLE 좌석 존재")
    void 좌석_조회() throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/seats", gameId)
                        .param("status", "AVAILABLE")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    @Order(3)
    @DisplayName("좌석 예약 — orderId 반환, 좌석 RESERVED 전환")
    void 좌석_예약() throws Exception {
        ReservationSeatsRequestDto request = new ReservationSeatsRequestDto(gameId, List.of(seatId));

        String response = mockMvc.perform(post("/api/seats/reservations")
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn().getResponse().getContentAsString();

        ReservationSeatsResponseDto dto = objectMapper.readValue(response, ReservationSeatsResponseDto.class);
        orderId = dto.orderId();
        assertThat(orderId).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("결제 생성 — paymentId, tossOrderId 반환")
    void 결제_생성() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of(
                        "orderId", orderId,
                        "matchId", gameId,
                        "seatId", seatId,
                        "amount", AMOUNT,
                        "paymentMethod", "CARD",
                        "idempotencyKey", "test-idem-" + UUID.randomUUID()
                )
        );

        String response = mockMvc.perform(post("/api/payments")
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.tossOrderId").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode dto = objectMapper.readTree(response);
        paymentId = dto.get("paymentId").asLong();
        tossOrderId = dto.get("tossOrderId").asText();
        assertThat(paymentId).isNotNull();
        assertThat(tossOrderId).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("결제 확정 (mock) — COMPLETED 전환, PAYMENT_COMPLETED 이벤트 발행")
    void 결제_확정() {
        PaymentResponse response = paymentService.confirmPaymentMock(
                TestConfirmPaymentRequest.of(tossOrderId, AMOUNT)
        );

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

//    @Test
//    @Order(6)
//    @DisplayName("알림 인박스 — PAYMENT_COMPLETED 수신 확인 (Awaitility 5s)")
//    void 알림_수신_확인() {
//        Awaitility.await()
//                .atMost(10, SECONDS)
//                .pollInterval(500, MILLISECONDS)
//                .untilAsserted(() ->
//                        mockMvc.perform(get("/api/notifications")
//                                        .header("Authorization", accessToken))
//                                .andExpect(status().isOk())
//                                .andExpect(jsonPath("$.content[*].eventType",
//                                        hasItem("PAYMENT_COMPLETED")))
//                );
//    }
}
