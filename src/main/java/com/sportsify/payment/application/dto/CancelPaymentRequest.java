package com.sportsify.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelPaymentRequest {

    @NotBlank(message = "cancelReason is required")
    private String cancelReason;
}