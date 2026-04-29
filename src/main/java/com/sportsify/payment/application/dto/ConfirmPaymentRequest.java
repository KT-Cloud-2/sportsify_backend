package com.sportsify.payment.application.dto;

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

    @NotBlank(message = "orderId는 필수입니다.")
    private String orderId;

    @NotNull(message = "amount는 필수입니다.")
    @Positive(message = "amount는 0보다 커야 합니다.")
    private Long amount;
}