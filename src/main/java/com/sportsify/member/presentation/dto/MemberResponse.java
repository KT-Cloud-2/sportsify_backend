package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.MemberResult;

import java.time.LocalDateTime;

public record MemberResponse(
        Long memberId,
        String email,
        String nickname,
        LocalDateTime createdAt
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
