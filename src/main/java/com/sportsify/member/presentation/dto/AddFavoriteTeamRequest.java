package com.sportsify.member.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddFavoriteTeamRequest(
        @NotNull Long teamId,
        @Positive Integer priority
) {
}
