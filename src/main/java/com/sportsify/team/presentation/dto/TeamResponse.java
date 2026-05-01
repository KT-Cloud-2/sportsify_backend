package com.sportsify.team.presentation.dto;

import com.sportsify.team.application.dto.TeamResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 응답")
public record TeamResponse(
        @Schema(description = "팀 ID", example = "1") Long teamId,
        @Schema(description = "팀 정식 명칭", example = "KIA 타이거즈") String name,
        @Schema(description = "팀 약칭", example = "KIA", nullable = true) String shortName,
        @Schema(description = "종목 (BASEBALL | FOOTBALL | BASKETBALL)", example = "BASEBALL") String sportType,
        @Schema(description = "로고 이미지 URL", nullable = true) String logoUrl,
        @Schema(description = "활동 중인 팀 여부", example = "true") boolean active
) {
    public static TeamResponse from(TeamResult result) {
        return new TeamResponse(
                result.teamId(),
                result.name(),
                result.shortName(),
                result.sportType(),
                result.logoUrl(),
                result.active()
        );
    }
}
