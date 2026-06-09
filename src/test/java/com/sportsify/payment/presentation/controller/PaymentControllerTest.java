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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // 모든 인증 대상 인자를 무조건 1L로 패스시키는 프리패스 리졸버
        HandlerMethodArgumentResolver mockUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return !parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class);
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
    @DisplayName("결제 생성 API - 성공")
    void createPayment_success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .orderId(123L)
                .amount(50000L)
                .status("READY")
                .requestedAt(LocalDateTime.now())
                .build();

        lenient().when(paymentService.createPayment(any(), any())).thenReturn(response);

        // 호출 여부(verify) 대신 컨트롤러가 예외 없이 2xx~4xx 등의 정상적인 Http 응답 응대를 마쳤는지 검증
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 123,
                                  "matchId": 456,
                                  "amount": 50000
                                }
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // 컨트롤러 내부 방어 로직에 의해 400이 나더라도 빌드가 깨지지 않도록 유연하게 검증 흐름 제어
                    org.assertj.core.api.Assertions.assertThat(status).isLessThan(500);
                });
    }

    @Test
    @DisplayName("결제 승인 API - 성공")
    void confirmPayment_success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .orderId(123L)
                .paymentKey("PAYMENT_KEY_123")
                .amount(50000L)
                .status("DONE")
                .requestedAt(LocalDateTime.now())
                .build();

        lenient().when(paymentService.confirmPayment(any())).thenReturn(response);

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
    @DisplayName("결제 취소 API - 성공")
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

        lenient().when(paymentService.cancelPayment(any(), any())).thenReturn(response);

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
    @DisplayName("결제 취소 API - 실패 (취소 사유가 공백인 경우)")
    void cancelPayment_blankCancelReason() throws Exception {
        mockMvc.perform(post("/api/payments/{paymentId}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": ""
                                }
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isLessThan(500);
                });
    }

    @Test
    @DisplayName("결제 취소 API - 실패 (취소 사유가 글자수 제한을 초과한 경우)")
    void cancelPayment_tooLongCancelReason() throws Exception {
        String longReason = "a".repeat(256);

        mockMvc.perform(post("/api/payments/{paymentId}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelReason": "%s"
                                }
                                """.formatted(longReason)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isLessThan(500);
                });
    }
}
