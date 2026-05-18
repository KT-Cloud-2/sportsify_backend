package com.sportsify.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConfirmPaymentRequest {

    @NotBlank(message = "paymentKey는 필수입니다.")
    private String paymentKey;

    @JsonAlias("orderId")
    @NotBlank(message = "tossOrderId는 필수입니다.")
    private String tossOrderId;

    @NotNull(message = "amount는 필수입니다.")
    @Positive(message = "amount는 0보다 커야 합니다.")
    private Long amount;
}
