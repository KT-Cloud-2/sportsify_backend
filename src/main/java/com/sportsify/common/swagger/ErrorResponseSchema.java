package com.sportsify.common.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

/** Swagger 에러 응답 스키마 전용 모델. 실제 사용 금지. */
@Schema(name = "ErrorResponse", description = "공통 에러 응답")
public record ErrorResponseSchema(
        @Schema(description = "성공 여부", example = "false") boolean success,
        @Schema(description = "데이터", nullable = true, example = "null") Object data,
        @Schema(description = "에러 상세") ErrorInfo error,
        @Schema(description = "응답 시각 (ISO-8601)", example = "2026-04-29T12:00:00Z") String timestamp
) {
    @Schema(name = "ErrorInfo")
    public record ErrorInfo(
            @Schema(description = "에러 코드", example = "INVALID_INPUT") String code,
            @Schema(description = "에러 메시지", example = "입력값이 올바르지 않습니다.") String message,
            @Schema(description = "상세 정보", nullable = true) String detail
    ) {}
}
