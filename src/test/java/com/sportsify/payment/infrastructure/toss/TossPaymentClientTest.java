package com.sportsify.payment.infrastructure.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsify.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.sportsify.payment.infrastructure.toss.dto.TossConfirmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentClientTest {

    private MockRestServiceServer mockServer;
    private TossPaymentClient tossPaymentClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        tossPaymentClient = new TossPaymentClient(restClient);
        ReflectionTestUtils.setField(tossPaymentClient, "secretKey", "test-secret-key");
        ReflectionTestUtils.setField(tossPaymentClient, "confirmUrl", "https://api.tosspayments.com/v1/payments/confirm");
        ReflectionTestUtils.setField(tossPaymentClient, "cancelUrl", "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel");
    }

    @Test
    @DisplayName("결제 승인 성공 - Toss API 정상 응답 시 TossConfirmResponse 반환")
    void confirm_success() throws Exception {
        TossConfirmRequest request = TossConfirmRequest.builder()
                .paymentKey("PAYMENT_KEY_123")
                .orderId("ORDER_1_TEST")
                .amount(50000L)
                .build();

        TossConfirmResponse mockResponse = TossConfirmResponse.builder()
                .paymentKey("PAYMENT_KEY_123")
                .orderId("ORDER_1_TEST")
                .totalAmount(50000L)
                .status("DONE")
                .approvedAt("2026-06-11T10:00:00+09:00")
                .method("CARD")
                .build();

        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic dGVzdC1zZWNyZXQta2V5Og=="))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(mockResponse),
                        MediaType.APPLICATION_JSON
                ));

        TossConfirmResponse response = tossPaymentClient.confirm(request);

        assertThat(response.getPaymentKey()).isEqualTo("PAYMENT_KEY_123");
        assertThat(response.getOrderId()).isEqualTo("ORDER_1_TEST");
        assertThat(response.getTotalAmount()).isEqualTo(50000L);
        assertThat(response.getStatus()).isEqualTo("DONE");
        mockServer.verify();
    }

    @Test
    @DisplayName("결제 승인 실패 - Toss API 4xx 응답 시 예외 발생")
    void confirm_4xxError() throws Exception {
        TossConfirmRequest request = TossConfirmRequest.builder()
                .paymentKey("PAYMENT_KEY_123")
                .orderId("ORDER_1_TEST")
                .amount(50000L)
                .build();

        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> tossPaymentClient.confirm(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Toss confirm API 호출 실패");

        mockServer.verify();
    }

    @Test
    @DisplayName("결제 승인 실패 - Toss API 5xx 응답 시 예외 발생")
    void confirm_5xxError() throws Exception {
        TossConfirmRequest request = TossConfirmRequest.builder()
                .paymentKey("PAYMENT_KEY_123")
                .orderId("ORDER_1_TEST")
                .amount(50000L)
                .build();

        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> tossPaymentClient.confirm(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Toss confirm API 호출 실패");

        mockServer.verify();
    }

    @Test
    @DisplayName("결제 취소 성공 - Toss API 정상 응답")
    void cancel_success() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/PAYMENT_KEY_123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic dGVzdC1zZWNyZXQta2V5Og=="))
                .andRespond(withSuccess());

        tossPaymentClient.cancel("PAYMENT_KEY_123", "사용자 취소");

        mockServer.verify();
    }

    @Test
    @DisplayName("결제 취소 실패 - Toss API 4xx 응답 시 예외 발생")
    void cancel_4xxError() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/PAYMENT_KEY_123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> tossPaymentClient.cancel("PAYMENT_KEY_123", "사용자 취소"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Toss cancel API 호출 실패");

        mockServer.verify();
    }

    @Test
    @DisplayName("결제 취소 실패 - Toss API 5xx 응답 시 예외 발생")
    void cancel_5xxError() {
        mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/PAYMENT_KEY_123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> tossPaymentClient.cancel("PAYMENT_KEY_123", "사용자 취소"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Toss cancel API 호출 실패");

        mockServer.verify();
    }
}
