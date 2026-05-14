package com.sportsify.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreatePaymentRequest {

    @NotNull(message = "orderId는 필수입니다.")
    private Long orderId;

    @NotNull(message = "matchId는 필수입니다.")
    private Long matchId;

    @NotNull(message = "seatId는 필수입니다.")
    private Long seatId;

    @NotNull(message = "amount는 필수입니다.")
    @Positive(message = "amount는 0보다 커야 합니다.")
    private Long amount;

    @NotBlank(message = "paymentMethod는 필수입니다.")
    private String paymentMethod;

    @NotBlank(message = "idempotencyKey는 필수입니다.")
    private String idempotencyKey;
}
