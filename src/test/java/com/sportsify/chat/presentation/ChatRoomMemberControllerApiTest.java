package com.sportsify.chat.presentation;

import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberInvitesResponse;
import com.sportsify.chat.application.chatRoomMember.dto.ChatRoomMemberResponse;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.presentation.chatRoomMember.controller.ChatRoomMemberController;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sportsify.common.exception.BusinessException;
import com.sportsify.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

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

        mockMvc.perform(delete("/api/chat/rooms/{roomId}/leave", ROOM_ID)
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

    @Test
    @DisplayName("GET /api/chat/rooms/getMyInvites — 200 내 초대 목록 조회 성공")
    void 내_초대목록_조회_성공() throws Exception {
        ChatRoomMemberInvitesResponse response = new ChatRoomMemberInvitesResponse(List.of());
        given(chatRoomMemberService.getMyInvites(eq(MEMBER_ID))).willReturn(response);

        mockMvc.perform(get("/api/chat/rooms/getMyInvites")
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invites").isArray());
    }

    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/reject — 204 초대 거부 성공")
    void 초대_거부_성공() throws Exception {
        mockMvc.perform(post("/api/chat/rooms/{roomId}/reject", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isNoContent());
    }

    // ──────────────────────── API 실패 Test ────────────────────────

    /**
     * DIRECT 방에는 추가 초대가 허용되지 않으므로 서비스가 BUSINESS_RULE_VIOLATION을 던지고 422로 응답해야 한다.
     * 실패 포인트: 상태 코드가 500으로 뭉개지면 클라이언트가 UI에서 적절한 안내를 표시할 수 없음.
     */
    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/invite — 422 DIRECT 방에 초대 시도하면 실패")
    void DIRECT방_초대_422() throws Exception {
        given(chatRoomMemberService.invite(eq(ROOM_ID), eq(MEMBER_ID), eq(INVITEE_ID)))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "DIRECT 방에는 추가 초대가 불가합니다."));

        mockMvc.perform(post("/api/chat/rooms/{roomId}/invite", ROOM_ID)
                        .param("inviteeId", String.valueOf(INVITEE_ID))
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    /**
     * 서비스가 NOT_FOUND를 던지면 404로 응답해야 한다.
     * 존재하지 않는 방에 입장을 시도하는 경우.
     */
    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/join — 404 존재하지 않는 채팅방 입장 시 실패")
    void 채팅방_입장_없는방_404() throws Exception {
        given(chatRoomMemberService.join(eq(ROOM_ID), eq(MEMBER_ID)))
                .willThrow(new BusinessException(ErrorCode.NOT_FOUND, "채팅방이 없습니다."));

        mockMvc.perform(post("/api/chat/rooms/{roomId}/join", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * BAN된 멤버가 입장하면 BUSINESS_RULE_VIOLATION(422)이 반환되어야 한다.
     * 실패 포인트: 상태 코드가 500으로 뭉개지면 클라이언트가 오류 원인을 알 수 없음.
     */
    @Test
    @DisplayName("POST /api/chat/rooms/{roomId}/join — 422 BAN된 멤버가 입장 시도하면 실패")
    void 채팅방_입장_BAN멤버_422() throws Exception {
        given(chatRoomMemberService.join(eq(ROOM_ID), eq(MEMBER_ID)))
                .willThrow(new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "BAN된 멤버입니다."));

        mockMvc.perform(post("/api/chat/rooms/{roomId}/join", ROOM_ID)
                        .header("Authorization", bearerToken(MEMBER_ID, "USER")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }
}
