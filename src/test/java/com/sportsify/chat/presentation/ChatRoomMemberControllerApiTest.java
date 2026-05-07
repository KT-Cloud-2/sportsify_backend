package com.sportsify.chat.presentation;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.presentation.chatRoomMember.controller.ChatRoomMemberController;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomMemberController.class)
class ChatRoomMemberControllerApiTest extends WebMvcTestSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long INVITEE_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
    @MockitoBean
    private ChatRoomMemberService chatRoomMemberService;

    // ──────────────────────── API 성공 Test ────────────────────────

    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/join — 201 채팅방 입장 성공")
    void 채팅방_입장_성공() throws Exception {
        ChatRoomMemberResponse response = new ChatRoomMemberResponse(ROOM_ID, MEMBER_ID, "JOINED", NOW);
        given(chatRoomMemberService.join(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(post("/api/chat/rooms/{roomId}/join", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.status").value("JOINED"));
    }

    @Test
    @DisplayName("DELETE /api/chat/rooms/{roomId}/invite — 200 채팅방 나가기 성공")
    void 채팅방_나가기_성공() throws Exception {
        ChatRoomMemberResponse response = new ChatRoomMemberResponse(ROOM_ID, MEMBER_ID, "LEFT", NOW);
        given(chatRoomMemberService.leave(eq(ROOM_ID), eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(delete("/api/chat/rooms/{roomId}/invite", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.status").value("LEFT"));
    }

    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/invite — 201 참여자 초대 성공")
    void 참여자_초대_성공() throws Exception {
        ChatRoomMemberResponse response = new ChatRoomMemberResponse(ROOM_ID, INVITEE_ID, "INVITED", NOW);
        given(chatRoomMemberService.invite(eq(ROOM_ID), eq(MEMBER_ID), eq(INVITEE_ID))).willReturn(response);

        mockMvc.perform(post("/api/chat/rooms/{roomId}/invite", ROOM_ID)
                        .param("inviteeId", String.valueOf(INVITEE_ID))
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.memberId").value(INVITEE_ID))
                .andExpect(jsonPath("$.status").value("INVITED"));
    }

    @Test
    @DisplayName("PATCH /api/chat/rooms/{roomId}/notification — 200 알림 설정 변경 성공")
    void 알림_설정_변경_성공() throws Exception {
        ChatRoomMemberResponse response = new ChatRoomMemberResponse(ROOM_ID, MEMBER_ID, "JOINED", NOW);
        given(chatRoomMemberService.changeNotification(eq(ROOM_ID), eq(MEMBER_ID), eq(true))).willReturn(response);

        mockMvc.perform(patch("/api/chat/rooms/{roomId}/notification", ROOM_ID)
                        .param("enabled", "true")
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.status").value("JOINED"));
    }

    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/ban — 200 채팅방 멤버 BAN 성공")
    void 채팅방_멤버_BAN_성공() throws Exception {
        Long targetId = 3L;
        ChatRoomMemberResponse response = new ChatRoomMemberResponse(ROOM_ID, targetId, "BANNED", NOW);
        given(chatRoomMemberService.ban(eq(ROOM_ID), eq(MEMBER_ID), eq(targetId))).willReturn(response);

        mockMvc.perform(post("/api/chat/rooms/{roomId}/ban", ROOM_ID)
                        .param("targetId", String.valueOf(targetId))
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.memberId").value(targetId))
                .andExpect(jsonPath("$.status").value("BANNED"));
    }
}
