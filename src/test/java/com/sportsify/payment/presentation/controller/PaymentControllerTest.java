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
    @DisplayName("cancel payment api success")
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

        mockMvc.perform(post("/api/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "user requested cancel"
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
    @DisplayName("cancel payment fails when cancel reason is blank")
    void cancelPayment_blankCancelReason() throws Exception {
        Long paymentId = 1L;

        mockMvc.perform(post("/api/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).cancelPayment(eq(paymentId), any(CancelPaymentRequest.class));
    }

    @Test
    @DisplayName("cancel payment fails when cancel reason is too long")
    void cancelPayment_tooLongCancelReason() throws Exception {
        Long paymentId = 1L;
        String longReason = "a".repeat(256);

        mockMvc.perform(post("/api/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "%s"
                                }
                                """.formatted(longReason)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).cancelPayment(eq(paymentId), any(CancelPaymentRequest.class));
    }
}
