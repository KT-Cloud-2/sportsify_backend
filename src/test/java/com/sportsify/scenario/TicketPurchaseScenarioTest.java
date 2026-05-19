package com.sportsify.scenario;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(1)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 1] 티켓 구매 전체 여정")
class TicketPurchaseScenarioTest extends ScenarioTestSupport {

    // game_id=1: 잠실 두산vs LG ON_SALE, seat_id=2 AVAILABLE, price=50000
    private static final Long GAME_ID = 1L;
    private static final Long SEAT_ID = 2L;
    private static final Long AMOUNT = 50000L;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long memberId;
    private String accessToken;
    private Long orderId;
    private Long paymentId;
    private String tossOrderId;

    @BeforeAll
    void setUpOnce() throws Exception {
        cleanUp(jdbc);
        executeSeed();
        Member member = createMember(memberRepository, "purchase@test.com", "kakao-purchase-001");
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
        mockMvc.perform(get("/api/games/{gameId}/seats", GAME_ID)
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
        ReservationSeatsRequestDto request = new ReservationSeatsRequestDto(GAME_ID, List.of(SEAT_ID));

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
                        "matchId", GAME_ID,
                        "seatId", SEAT_ID,
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

        PaymentResponse dto = objectMapper.readValue(response, PaymentResponse.class);
        paymentId = dto.getPaymentId();
        tossOrderId = dto.getTossOrderId();
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

    @Test
    @Order(6)
    @DisplayName("알림 인박스 — PAYMENT_COMPLETED 수신 확인 (Awaitility 5s)")
    void 알림_수신_확인() {
        Awaitility.await()
                .atMost(10, SECONDS)
                .pollInterval(500, MILLISECONDS)
                .untilAsserted(() ->
                        mockMvc.perform(get("/api/notifications")
                                        .header("Authorization", accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[*].eventType",
                                        hasItem("PAYMENT_COMPLETED")))
                );
    }
}
