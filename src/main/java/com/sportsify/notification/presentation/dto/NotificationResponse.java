package com.sportsify.notification.presentation.dto;

import com.sportsify.notification.application.dto.NotificationResult;
import com.sportsify.common.notification.NotificationEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID") Long id,
        @Schema(description = "알림 이벤트 유형") NotificationEventType eventType,
        @Schema(description = "알림 페이로드 (이벤트 관련 부가 정보)") String payload,
        @Schema(description = "읽음 여부") boolean read,
        @Schema(description = "알림 생성 일시") LocalDateTime createdAt
) {
    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(
                result.id(),
                result.eventType(),
                result.payload(),
                result.read(),
                result.createdAt()
        );
    }
}
