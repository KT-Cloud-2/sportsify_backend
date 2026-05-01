package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.MemberResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "회원 정보 응답")
public record MemberResponse(
        @Schema(description = "회원 ID", example = "1") Long memberId,
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "닉네임", example = "응원왕") String nickname,
        @Schema(description = "가입 시각 (ISO-8601)", example = "2026-01-01T00:00:00") LocalDateTime createdAt
) {
    public static MemberResponse from(MemberResult result) {
        return new MemberResponse(
                result.memberId(),
                result.email(),
                result.nickname(),
                result.createdAt()
        );
    }
}
