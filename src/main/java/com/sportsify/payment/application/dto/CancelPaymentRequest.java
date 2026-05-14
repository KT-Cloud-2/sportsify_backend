package com.sportsify.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentRequest {

    @NotBlank(message = "취소 사유는 필수입니다.")
    @Size(max = 255, message = "취소 사유는 255자 이하여야 합니다.")
    private String cancelReason;
}
