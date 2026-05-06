package com.sportsify.member.presentation.dto;

public record OAuthCallbackResponse(
        String accessToken,
        String refreshToken
) {
}
