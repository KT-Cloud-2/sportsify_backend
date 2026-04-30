package com.sportsify.chat.application.chatRoom.dto;

import java.util.List;

public record ChatRoomListResponse(
        List<? extends Record> items,
        Long nextCursor,
        Boolean hasNext,
        Integer totalCount
) {
}
