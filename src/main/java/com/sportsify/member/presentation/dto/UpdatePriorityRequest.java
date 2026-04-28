package com.sportsify.member.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePriorityRequest(
        @NotNull @Min(1) Integer priority
) {
}
