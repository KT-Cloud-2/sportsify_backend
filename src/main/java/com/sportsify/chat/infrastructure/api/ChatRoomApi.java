package com.sportsify.chat.infrastructure.api;

import com.sportsify.chat.application.chatRoom.dto.*;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Chat_chatRoom", description = "채팅방 api")
@AuthRequiredApi
@CommonApiResponses
public interface ChatRoomApi {
    @SwaggerApi(summary = "5-1. 채팅방 생성")
    @SwaggerApiError(ErrorCode.INVALID_INPUT)
    @SwaggerApiError(ErrorCode.CONFLICT)
    ResponseEntity<ChatRoomResponse> create(
            Long memberId,
            @RequestBody CreateChatRoomRequest request
    );

    @SwaggerApi(summary = "5-2. 내 채팅방 목록 조회")
    @SwaggerApiError(ErrorCode.INVALID_INPUT)
    ResponseEntity<ChatRoomListResponse> getMyRooms(
            Long memberId,
            @RequestBody ChatRoomGetRequest request
    );

    @SwaggerApi(summary = "5-3. 게임별 채팅방 조회")
    ResponseEntity<ChatRoomListResponse> getRoomsByGameId(
            @ModelAttribute ChatRoomGetByGameRequest request,
            @PathVariable Long gameId
    );

    @SwaggerApi(summary = "5-4. 채팅방 상세 조회")
    @SwaggerApiError(ErrorCode.INVALID_INPUT)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<ChatRoomDetailResponse> getRoomDetail(
            Long memberId,
            @PathVariable Long roomId
    );

    @SwaggerApi(summary = "5-5. 채팅방 정보 수정")
    @SwaggerApiError(ErrorCode.INVALID_INPUT)
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<ChatRoomUpdateResponse> update(
            Long memberId,
            @PathVariable Long roomId,
            @RequestBody ChatRoomUpdateRequest request
    );

    @SwaggerApi(summary = "5-6. 채팅방 삭제", responseCode = "204")
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<Void> delete(
            Long memberId,
            @PathVariable Long roomId
    );

    @SwaggerApi(summary = "5-7. 채팅방 아카이브")
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<ChatRoomArchiveResponse> archive(
            Long memberId,
            @PathVariable Long roomId
    );

    @SwaggerApi(summary = "5-8. 채팅방 아카이브 복원")
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<ChatRoomArchiveResponse> unarchive(
            Long memberId,
            @PathVariable Long roomId
    );
}
