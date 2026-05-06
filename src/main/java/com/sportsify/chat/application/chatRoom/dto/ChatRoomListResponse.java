package com.sportsify.chat.application.chatRoom.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChatRoomListResponse(
        @Schema(description = "채팅방 정보", example = "items") List<? extends Record> items,
        @Schema(description = "조회 시작 채팅방 ID", example = "13") Long nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "True") Boolean hasNext,
        @Schema(description = "검색된 채팅방 수", example = "20") Integer totalCount
) {
}
