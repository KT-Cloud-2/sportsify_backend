package com.sportsify.chat.infrastructure.api;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Chat_chatRoomMember", description = "채팅방 멤버 api")
@AuthRequiredApi
@CommonApiResponses
public interface ChatRoomMemberApi {

    @SwaggerApi(summary = "5-7. 채팅방 입장")
    @SwaggerApiError(ErrorCode.CONFLICT)
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    ResponseEntity<ChatRoomMemberResponse> join(
            Long memberId,
            @PathVariable Long roomId
    );

    @SwaggerApi(summary = "5-8. 채팅방 나가기")
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    @SwaggerApiError(ErrorCode.CONFLICT)
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    ResponseEntity<ChatRoomMemberResponse> leave(
            Long memberId,
            @PathVariable Long roomId
    );

    @SwaggerApi(summary = "5-9. 참여자 초대")
    @SwaggerApiError(ErrorCode.INVALID_INPUT)
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.CONFLICT)
    ResponseEntity<ChatRoomMemberResponse> invite(
            Long memberId,
            @PathVariable Long roomId,
            @RequestParam Long inviteeId
    );

    @SwaggerApi(summary = "5-10. 알림 설정 변경")
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<ChatRoomMemberResponse> changeNotification(
            Long memberId,
            @PathVariable Long roomId,
            @RequestParam boolean enabled
    );

    @SwaggerApi(summary = "5-11. 채팅 이력 조회")
    @SwaggerApiError(ErrorCode.INVALID_INPUT)
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    @SwaggerApiError(ErrorCode.CONFLICT)
    ResponseEntity<ChatRoomMemberResponse> ban(
            Long memberId,
            @PathVariable Long roomId,
            @RequestParam Long targetId
    );
}
