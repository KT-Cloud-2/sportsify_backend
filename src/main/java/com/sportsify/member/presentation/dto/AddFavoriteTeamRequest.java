package com.sportsify.member.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "선호 팀 추가 요청")
public record AddFavoriteTeamRequest(
        @Schema(description = "추가할 팀 ID", example = "3") @NotNull Long teamId,
        @Schema(description = "선호 순위 (선택, 미지정 시 자동 할당)", example = "1", nullable = true)
        @Positive Integer priority
) {
}
