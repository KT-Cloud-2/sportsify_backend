package com.sportsify.chat.application.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MessageListResponse(
        @Schema(description = "메시지 내역들", example = "messages: [...]") List<? extends Record> messages,
        @Schema(description = "채팅방 멤버들의 읽은 마지막 message ID", example = "members: [...]") List<? extends Record> members,
        @Schema(description = "조회 시작 메시지 ID", example = "746") Long nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "True") boolean hasNext,
        @Schema(description = "검색된 메시지 수", example = "20") int totalCount
) {
}
