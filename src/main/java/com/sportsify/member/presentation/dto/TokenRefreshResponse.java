package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.TokenPairResult;

public record TokenRefreshResponse(String accessToken, String refreshToken) {

    public static TokenRefreshResponse from(TokenPairResult result) {
        return new TokenRefreshResponse(result.accessToken(), result.refreshToken());
    }
}
