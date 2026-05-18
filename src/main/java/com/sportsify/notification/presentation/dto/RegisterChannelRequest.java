package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.domain.model.NotificationChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 채널 등록 요청")
public record RegisterChannelRequest(
        @Schema(description = "채널 유형 (EMAIL, MQTT 등)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull NotificationChannelType channelType,

        @Schema(description = "채널 대상 (이메일 주소 등)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String channelTarget
) {}
