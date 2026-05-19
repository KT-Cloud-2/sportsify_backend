package com.sportsify.scenario;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.payment.application.dto.CancelPaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(3)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 3] 결제 취소 후 좌석 복구")
class PaymentCancelScenarioTest extends ScenarioTestSupport {

    // game_id=1: 잠실 두산vs LG ON_SALE, seat_id=3 AVAILABLE
    private static final Long GAME_ID = 1L;
    private static final Long SEAT_ID = 3L;
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

    @BeforeEach
    void resetMocks() {
        willDoNothing().given(tossPaymentClient).cancel(any(), any());
    }

    @BeforeAll
    void setUpOnce() throws Exception {
        cleanUp(jdbc);
        executeSeed();
        Member member = createMember(memberRepository, "cancel@test.com", "kakao-cancel-001");
        memberId = member.getId();
        accessToken = bearerToken(memberId);
    }

    @Test
    @Order(1)
    @DisplayName("좌석 조회 — AVAILABLE 상태 확인")
    void 좌석_조회() throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/seats", GAME_ID)
                        .param("status", "AVAILABLE")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    @Order(2)
    @DisplayName("좌석 예약 → RESERVED 전환")
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
    @Order(3)
    @DisplayName("결제 생성")
    void 결제_생성() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of(
                        "orderId", orderId,
                        "matchId", GAME_ID,
                        "seatId", SEAT_ID,
                        "amount", AMOUNT,
                        "paymentMethod", "CARD",
                        "idempotencyKey", "cancel-idem-" + UUID.randomUUID()
                )
        );

        String response = mockMvc.perform(post("/api/payments")
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andReturn().getResponse().getContentAsString();

        PaymentResponse dto = objectMapper.readValue(response, PaymentResponse.class);
        paymentId = dto.getPaymentId();
        tossOrderId = dto.getTossOrderId();
        assertThat(paymentId).isNotNull();
        assertThat(tossOrderId).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("결제 확정 (mock)")
    void 결제_확정() {
        PaymentResponse response = paymentService.confirmPaymentMock(
                TestConfirmPaymentRequest.of(tossOrderId, AMOUNT)
        );
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(5)
    @DisplayName("결제 취소 — CANCELED 전환 (tossPaymentClient.cancel mock)")
    void 결제_취소() throws Exception {
        String body = objectMapper.writeValueAsString(new CancelPaymentRequest("테스트 취소"));

        mockMvc.perform(post("/api/payments/{paymentId}/cancel", paymentId)
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELED")));
    }

    @Test
    @Order(6)
    @DisplayName("좌석 상태 복구 — AVAILABLE 전환 확인 (PaymentEventListener 검증)")
    void 좌석_복구_확인() throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/seats", GAME_ID)
                        .param("status", "AVAILABLE")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.seatId == " + SEAT_ID + ")]").exists());
    }
}
