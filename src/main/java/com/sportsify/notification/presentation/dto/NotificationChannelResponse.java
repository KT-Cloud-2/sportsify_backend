package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationChannelResult;
import com.sportsify.notification.domain.model.NotificationChannelType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 채널 응답")
public record NotificationChannelResponse(
        @Schema(description = "채널 ID") Long id,
        @Schema(description = "채널 유형 (EMAIL, MQTT 등)") NotificationChannelType channelType,
        @Schema(description = "채널 대상 (이메일 주소 등)") String channelTarget,
        @Schema(description = "채널 활성화 여부") boolean enabled
) {
    public static NotificationChannelResponse from(NotificationChannelResult result) {
        return new NotificationChannelResponse(
                result.id(),
                result.channelType(),
                result.channelTarget(),
                result.enabled()
        );
    }
}
