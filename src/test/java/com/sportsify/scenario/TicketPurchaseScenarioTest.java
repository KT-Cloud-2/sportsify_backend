package com.sportsify.scenario;

import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.payment.application.dto.ConfirmPaymentRequest;
import com.sportsify.payment.application.dto.CreatePaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsRequestDto;
import com.sportsify.ticketing.presentation.dto.ReservationSeatsResponseDto;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(1)
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

    private static Long memberId;
    private static String accessToken;
    private static Long orderId;
    private static Long paymentId;
    private static String tossOrderId;

    @BeforeEach
    void setUpMember() {
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
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
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
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
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
        CreatePaymentRequest request = new CreatePaymentRequest();
        // 리플렉션 없이 JSON으로 직접 구성
        String body = objectMapper.writeValueAsString(
                new java.util.HashMap<>() {{
                    put("orderId", orderId);
                    put("matchId", GAME_ID);
                    put("seatId", SEAT_ID);
                    put("amount", AMOUNT);
                    put("paymentMethod", "CARD");
                    put("idempotencyKey", "test-idem-" + UUID.randomUUID());
                }}
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
        // TossPaymentClient는 MockitoBean — 실제 외부 호출 없음
        ConfirmPaymentRequest request = new ConfirmPaymentRequest();
        // confirmPaymentMock은 tossPaymentClient 호출 없이 직접 완료 처리
        PaymentResponse response = paymentService.confirmPaymentMock(
                buildConfirmRequest(tossOrderId, AMOUNT)
        );

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(6)
    @DisplayName("알림 인박스 — PAYMENT_COMPLETED 수신 확인 (Awaitility 5s)")
    void 알림_수신_확인() {
        Awaitility.await()
                .atMost(5, SECONDS)
                .untilAsserted(() ->
                        mockMvc.perform(get("/api/notifications")
                                        .header("Authorization", accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[*].eventType",
                                        hasItem("PAYMENT_COMPLETED")))
                );
    }

    private ConfirmPaymentRequest buildConfirmRequest(String tossOrderId, Long amount) {
        try {
            ConfirmPaymentRequest req = new ConfirmPaymentRequest();
            var clazz = ConfirmPaymentRequest.class;
            setField(clazz, req, "tossOrderId", tossOrderId);
            setField(clazz, req, "paymentKey", "MOCK_" + tossOrderId);
            setField(clazz, req, "amount", amount);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Class<?> clazz, Object target, String fieldName, Object value) throws Exception {
        var field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
