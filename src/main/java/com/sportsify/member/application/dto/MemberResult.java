package com.sportsify.member.application.dto;

import com.sportsify.member.domain.model.Member;

import java.time.LocalDateTime;

public record MemberResult(
        Long memberId,
        String email,
        String nickname,
        LocalDateTime createdAt
) {
    public static MemberResult from(Member member) {
        return new MemberResult(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getCreatedAt()
        );
    }
}
