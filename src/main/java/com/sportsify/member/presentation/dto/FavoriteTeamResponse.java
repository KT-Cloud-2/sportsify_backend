package com.sportsify.member.presentation.dto;

import com.sportsify.member.application.dto.FavoriteTeamResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선호 팀 응답")
public record FavoriteTeamResponse(
        @Schema(description = "선호 팀 레코드 ID", example = "10") Long favoriteTeamId,
        @Schema(description = "팀 ID", example = "3") Long teamId,
        @Schema(description = "팀 이름", example = "KIA 타이거즈") String teamName,
        @Schema(description = "팀 약칭", example = "KIA", nullable = true) String shortName,
        @Schema(description = "종목 (BASEBALL | FOOTBALL | BASKETBALL)", example = "BASEBALL") String sportType,
        @Schema(description = "선호 순위", example = "1") int priority
) {
    public static FavoriteTeamResponse from(FavoriteTeamResult result) {
        return new FavoriteTeamResponse(
                result.favoriteTeamId(),
                result.teamId(),
                result.teamName(),
                result.shortName(),
                result.sportType(),
                result.priority()
        );
    }
}
