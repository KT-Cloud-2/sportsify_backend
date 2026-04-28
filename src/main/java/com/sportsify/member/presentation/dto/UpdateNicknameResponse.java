package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.MemberResult;

public record UpdateNicknameResponse(Long memberId, String nickname) {

    public static UpdateNicknameResponse from(MemberResult result) {
        return new UpdateNicknameResponse(result.memberId(), result.nickname());
    }
}
