package com.sportsify.member.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record TokenRefreshRequest(
        @Schema(description = "유효한 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank String refreshToken
) {
}
