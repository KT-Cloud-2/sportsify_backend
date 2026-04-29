package com.sportsify.member.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank String refreshToken
) {
}
