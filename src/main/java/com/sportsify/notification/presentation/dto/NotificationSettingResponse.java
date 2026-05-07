package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationSettingResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 응답")
public record NotificationSettingResponse(
        @Schema(description = "티켓 오픈 알림 활성화 여부") boolean ticketOpenAlert,
        @Schema(description = "경기 시작 알림 활성화 여부") boolean gameStartAlert,
        @Schema(description = "결제 완료 알림 활성화 여부") boolean paymentAlert,
        @Schema(description = "채팅 멘션 알림 활성화 여부") boolean chatMentionAlert
) {
    public static NotificationSettingResponse from(NotificationSettingResult result) {
        return new NotificationSettingResponse(
                result.ticketOpenAlert(),
                result.gameStartAlert(),
                result.paymentAlert(),
                result.chatMentionAlert()
        );
    }
}
