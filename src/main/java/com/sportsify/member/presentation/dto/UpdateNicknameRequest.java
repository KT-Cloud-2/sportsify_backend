package com.sportsify.member.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 수정 요청")
public record UpdateNicknameRequest(
        @Schema(description = "변경할 닉네임 (2~20자)", example = "새닉네임")
        @NotBlank @Size(min = 2, max = 20) String nickname
) {
}
