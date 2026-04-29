package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.TokenPairResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 갱신 응답")
public record TokenRefreshResponse(
        @Schema(description = "새 액세스 토큰 (30분 만료)", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(description = "새 리프레시 토큰 (30일 만료)", example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken
) {

    public static TokenRefreshResponse from(TokenPairResult result) {
        return new TokenRefreshResponse(result.accessToken(), result.refreshToken());
    }
}
