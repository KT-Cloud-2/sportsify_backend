package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.MemberResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 수정 응답")
public record UpdateNicknameResponse(
        @Schema(description = "회원 ID", example = "1") Long memberId,
        @Schema(description = "변경된 닉네임", example = "새닉네임") String nickname
) {

    public static UpdateNicknameResponse from(MemberResult result) {
        return new UpdateNicknameResponse(result.memberId(), result.nickname());
    }
}
