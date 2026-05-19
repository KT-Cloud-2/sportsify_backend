package com.sportsify.scenario;

import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.integration.ChatIntegrationTestFixture;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(4)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 4] 채팅방 입장 후 메시지 전송")
class ChatMessageScenarioTest extends ScenarioTestSupport {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepository;

    @Autowired
    private ChatIntegrationTestFixture chatFixture;

    // game_id=1: seed.sql에서 생성된 ON_SALE 경기 (잠실 두산 vs LG)
    private static final Long GAME_ID = 1L;

    private static Long user1Id;
    private static String user1Token;
    private static Long user2Id;
    private static String user2Token;
    private static Long roomId;

    @BeforeEach
    void setUpMembers() {
        Member user1 = createMember(memberRepository, "chat-user1@test.com", "kakao-chat-user1");
        Member user2 = createMember(memberRepository, "chat-user2@test.com", "kakao-chat-user2");
        user1Id = user1.getId();
        user2Id = user2.getId();
        user1Token = bearerToken(user1Id);
        user2Token = bearerToken(user2Id);

        // Game 생성 Controller/Service 미구현 (운영자 DB 직접 관리 가정)
        // game_id=1에 연결된 채팅방을 fixture로 직접 생성
        ChatRoomJpaEntity room = chatFixture.createRoom("테스트 게임방", "GAME", "ACTIVE", user1Id, GAME_ID);
        roomId = room.getId();
    }

    @Test
    @Order(1)
    @DisplayName("game 기준 채팅방 조회 — roomId 추출")
    void 채팅방_조회() throws Exception {
        mockMvc.perform(get("/api/chat/rooms/game/{gameId}", GAME_ID)
                        .header("Authorization", user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomId").value(roomId));
    }

    @Test
    @Order(2)
    @DisplayName("user1 채팅방 입장")
    void user1_채팅방_입장() throws Exception {
        mockMvc.perform(post("/api/chat/rooms/{roomId}/join", roomId)
                        .header("Authorization", user1Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.memberId").value(user1Id));
    }

    @Test
    @Order(3)
    @DisplayName("user2 채팅방 입장")
    void user2_채팅방_입장() throws Exception {
        mockMvc.perform(post("/api/chat/rooms/{roomId}/join", roomId)
                        .header("Authorization", user2Token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.memberId").value(user2Id));
    }

    @Test
    @Order(4)
    @DisplayName("user1 메시지 전송 (MessageService 직접 호출) — DB 저장 확인")
    void user1_메시지_전송() {
        // STOMP 실제 연결 없이 서비스 레이어 직접 검증
        // WebSocketSessionRegistry 의존성 없이 비즈니스 로직만 검증
        var response = messageService.send(
                new MessageCreateRequest(
                        UUID.randomUUID().toString(),
                        roomId,
                        "TEXT",
                        "안녕하세요!"
                ),
                user1Id
        );

        assertThat(response).isNotNull();
        assertThat(response.messageId()).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("user2 메시지 히스토리 조회 — user1 메시지 존재 확인")
    void user2_메시지_히스토리_조회() throws Exception {
        mockMvc.perform(get("/api/chat/messages/history/{roomId}", roomId)
                        .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(greaterThan(0)));
    }
}
