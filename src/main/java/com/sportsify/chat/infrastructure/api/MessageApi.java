package com.sportsify.chat.infrastructure.api;

import com.sportsify.chat.application.message.dto.MessageDeleteResponse;
import com.sportsify.chat.application.message.dto.MessageListResponse;
import com.sportsify.chat.application.message.dto.MessagePageNationRequest;
import com.sportsify.common.exception.ErrorCode;
import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.common.swagger.SwaggerApiError;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Chat_message", description = "채팅 메시지 api")
@AuthRequiredApi
@CommonApiResponses
public interface MessageApi {

    @SwaggerApi(summary = "5-16. 채팅 이력 조회")
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<MessageListResponse> history(
            Long memberId,
            @PathVariable Long roomId,
            @RequestBody MessagePageNationRequest request
    );

    @SwaggerApi(summary = "5-18. 메시지 삭제")
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<MessageDeleteResponse> delete(
            Long memberId,
            @PathVariable Long messageId
    );

    @SwaggerApi(summary = "5-19. 채팅방 메시지 조회")
    @SwaggerApiError(ErrorCode.FORBIDDEN)
    @SwaggerApiError(ErrorCode.NOT_FOUND)
    ResponseEntity<MessageListResponse> getMessages(
            Long memberId,
            @PathVariable Long roomId,
            @RequestBody MessagePageNationRequest request
    );
}
