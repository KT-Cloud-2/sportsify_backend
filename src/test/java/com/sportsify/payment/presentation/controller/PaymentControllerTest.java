package com.sportsify.payment.presentation.controller;

import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver mockUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && Long.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return 1L;
            }
        };

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setCustomArgumentResolvers(mockUserResolver)
                .build();
    }

    @Test
    @DisplayName("결제 생성 API - 성공할 경우 200 OK를 반환한다")
    void createPayment_success() throws Exception {
        // given
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .orderId(123L)
                .amount(50000L)
                .status("READY")
                .requestedAt(LocalDateTime.now())
                .build();

        when(paymentService.createPayment(any(), any())).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                  "orderId": 123,
                  "matchId": 456,
                  "seatId": 1,
                  "amount": 50000,
                  "idempotencyKey": "test-idempotency-key",
                  "paymentMethod": "CARD"
                }
                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("결제 승인 API - 성공할 경우 200 OK를 반환한다")
    void confirmPayment_success() throws Exception {
        // given
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .orderId(123L)
                .paymentKey("PAYMENT_KEY_123")
                .amount(50000L)
                .status("DONE")
                .requestedAt(LocalDateTime.now())
                .build();

        when(paymentService.confirmPayment(any())).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentKey": "PAYMENT_KEY_123",
                                  "orderId": 123,
                                  "amount": 50000
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("결제 취소 API - 성공할 경우 200 OK를 반환한다")
    void cancelPayment_success() throws Exception {
        // given
        Long paymentId = 1L;

        PaymentResponse response = PaymentResponse.builder()
                .paymentId(paymentId)
                .orderId(123L)
                .paymentKey("PAYMENT_KEY_123")
                .amount(50000L)
                .paymentMethod("CARD")
                .status("CANCELED")
                .requestedAt(LocalDateTime.now())
                .build();

        when(paymentService.cancelPayment(any(), any())).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "user requested cancel"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("결제 취소 API - 취소 사유가 공백인 경우 400 Bad Request를 반환한다")
    void cancelPayment_blankCancelReason() throws Exception {
        // when & then
        mockMvc.perform(post("/api/payments/{paymentId}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("결제 취소 API - 취소 사유가 글자수 제한(255자)을 초과한 경우 400 Bad Request를 반환한다")
    void cancelPayment_tooLongCancelReason() throws Exception {
        // given
        String longReason = "a".repeat(256);

        // when & then
        mockMvc.perform(post("/api/payments/{paymentId}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "%s"
                                }
                                """.formatted(longReason)))
                .andExpect(status().isBadRequest());
    }
}
