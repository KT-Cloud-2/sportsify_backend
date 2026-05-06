package com.sportsify.chat.presentation;

import com.sportsify.chat.application.message.dto.MessageDeleteResponse;
import com.sportsify.chat.application.message.dto.MessageListResponse;
import com.sportsify.chat.application.message.dto.MessagePageNationRequest;
import com.sportsify.chat.application.message.dto.MessageSummaryResponse;
import com.sportsify.chat.application.message.dto.MessageResponse;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.presentation.message.controller.MessageController;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
class MessageControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private MessageService messageService;

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long MESSAGE_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 12, 0);

    // ──────────────────────── GET /api/chat/messages/history/{roomId} ────────────────────────

    @Test
    @DisplayName("GET /api/chat/messages/history/{roomId} — 200 채팅 이력 조회 성공")
    void 채팅_이력_조회_성공() throws Exception {
        MessageSummaryResponse item = new MessageSummaryResponse(MESSAGE_ID, ROOM_ID, "TEXT", "ACTIVE", NOW);
        MessageListResponse response = new MessageListResponse(List.of(item), null, false, 1);

        given(messageService.getHistory(any(MessagePageNationRequest.class), eq(ROOM_ID), eq(MEMBER_ID)))
                .willReturn(response);

        mockMvc.perform(get("/api/chat/messages/history/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessagePageNationRequest(null, 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    // ──────────────────────── DELETE /api/chat/messages/{messageId} ────────────────────────

    @Test
    @DisplayName("DELETE /api/chat/messages/{messageId} — 200 메시지 삭제 성공")
    void 메시지_삭제_성공() throws Exception {
        MessageDeleteResponse response = new MessageDeleteResponse(MESSAGE_ID, ROOM_ID, "DELETED");

        given(messageService.delete(MESSAGE_ID, MEMBER_ID)).willReturn(response);

        mockMvc.perform(delete("/api/chat/messages/{messageId}", MESSAGE_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageId").value(MESSAGE_ID))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.data.status").value("DELETED"));
    }

    // ──────────────────────── GET /api/chat/messages/getMessages/{roomId} ────────────────────────

    @Test
    @DisplayName("GET /api/chat/messages/getMessages/{roomId} — 200 채팅방 메시지 조회 성공")
    void 채팅방_메시지_조회_성공() throws Exception {
        MessageResponse item = new MessageResponse(MESSAGE_ID, MEMBER_ID, "TEXT", "안녕하세요", NOW);
        MessageListResponse response = new MessageListResponse(List.of(item), MESSAGE_ID, true, 1);

        given(messageService.getMessages(any(MessagePageNationRequest.class), eq(ROOM_ID), eq(MEMBER_ID)))
                .willReturn(response);

        mockMvc.perform(get("/api/chat/messages/getMessages/{roomId}", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessagePageNationRequest(null, 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value(MESSAGE_ID))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }
}
