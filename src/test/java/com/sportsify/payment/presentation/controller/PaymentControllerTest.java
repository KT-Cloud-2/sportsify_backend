package com.sportsify.payment.presentation.controller;

import com.sportsify.payment.application.dto.CancelPaymentRequest;
import com.sportsify.payment.application.dto.PaymentResponse;
import com.sportsify.payment.application.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("결제 취소 API 성공")
    void cancelPayment_success() throws Exception {
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

        given(paymentService.cancelPayment(eq(paymentId), any(CancelPaymentRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "사용자 요청 취소"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.orderId").value(123L))
                .andExpect(jsonPath("$.paymentKey").value("PAYMENT_KEY_123"))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.status").value("CANCELED"));

        verify(paymentService).cancelPayment(eq(paymentId), any(CancelPaymentRequest.class));
    }

    @Test
    @DisplayName("결제 취소 사유가 비어 있으면 400 응답")
    void cancelPayment_blankCancelReason_fail() throws Exception {
        Long paymentId = 1L;

        mockMvc.perform(post("/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("결제 취소 사유가 255자를 초과하면 400 응답")
    void cancelPayment_tooLongCancelReason_fail() throws Exception {
        Long paymentId = 1L;
        String tooLongReason = "a".repeat(256);

        mockMvc.perform(post("/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "%s"
                                }
                                """.formatted(tooLongReason)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).cancelPayment(any(), any());
    }
}
