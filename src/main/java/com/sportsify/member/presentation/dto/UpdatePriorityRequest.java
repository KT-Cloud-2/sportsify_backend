package com.sportsify.member.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "선호 팀 우선순위 수정 요청")
public record UpdatePriorityRequest(
        @Schema(description = "변경할 우선순위 (1 이상)", example = "2")
        @NotNull @Min(1) Integer priority
) {
}
