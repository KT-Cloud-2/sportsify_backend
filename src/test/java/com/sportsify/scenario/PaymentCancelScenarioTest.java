package com.sportsify.scenario;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.payment.application.dto.CancelPaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(3)
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

    private static Long memberId;
    private static String accessToken;
    private static Long orderId;
    private static Long paymentId;
    private static String tossOrderId;

    @BeforeEach
    void setUpMember() {
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
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
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
    }

    @Test
    @Order(3)
    @DisplayName("결제 생성")
    void 결제_생성() throws Exception {
        String body = objectMapper.writeValueAsString(
                new java.util.HashMap<>() {{
                    put("orderId", orderId);
                    put("matchId", GAME_ID);
                    put("seatId", SEAT_ID);
                    put("amount", AMOUNT);
                    put("paymentMethod", "CARD");
                    put("idempotencyKey", "cancel-idem-" + UUID.randomUUID());
                }}
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
    }

    @Test
    @Order(4)
    @DisplayName("결제 확정 (mock)")
    void 결제_확정() {
        PaymentResponse response = paymentService.confirmPaymentMock(
                buildConfirmRequest(tossOrderId, AMOUNT)
        );
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(5)
    @DisplayName("결제 취소 — CANCELED 전환 (tossPaymentClient.cancel mock)")
    void 결제_취소() throws Exception {
        // tossPaymentClient.cancel()은 MockitoBean — 실제 Toss API 호출 없음
        willDoNothing().given(tossPaymentClient).cancel(any(), any());

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

    private com.sportsify.payment.application.dto.ConfirmPaymentRequest buildConfirmRequest(String tossOrderId, Long amount) {
        try {
            com.sportsify.payment.application.dto.ConfirmPaymentRequest req =
                    new com.sportsify.payment.application.dto.ConfirmPaymentRequest();
            setField(req, "tossOrderId", tossOrderId);
            setField(req, "paymentKey", "MOCK_" + tossOrderId);
            setField(req, "amount", amount);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
