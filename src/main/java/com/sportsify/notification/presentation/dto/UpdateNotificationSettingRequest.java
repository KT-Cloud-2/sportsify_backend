package com.sportsify.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 설정 수정 요청")
public record UpdateNotificationSettingRequest(
        @Schema(description = "티켓 오픈 알림 활성화 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean ticketOpenAlert,

        @Schema(description = "경기 시작 알림 활성화 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean gameStartAlert,

        @Schema(description = "결제 완료 알림 활성화 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean paymentAlert,

        @Schema(description = "채팅 멘션 알림 활성화 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean chatMentionAlert
) {}
