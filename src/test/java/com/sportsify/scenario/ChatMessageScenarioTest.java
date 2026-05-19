package com.sportsify.scenario;

import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.integration.ChatIntegrationTestFixture;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(4)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 4] 채팅방 입장 후 메시지 전송")
class ChatMessageScenarioTest extends ScenarioTestSupport {

    // game_id=1: seed.sql에서 생성된 ON_SALE 경기 (잠실 두산 vs LG)
    private static final Long GAME_ID = 1L;

    @Autowired private MemberJpaRepository memberRepository;
    @Autowired private ChatIntegrationTestFixture chatFixture;
    @Autowired private ChatRoomMemberService chatRoomMemberService;
    @Autowired private MessageService messageService;
    @Autowired private JdbcTemplate jdbc;

    private Long user1Id;
    private String user1Token;
    private Long user2Id;
    private String user2Token;
    private Long roomId;

    @BeforeAll
    void setUpOnce() throws Exception {
        cleanUp(jdbc);
        executeSeed();

        Member user1 = createMember(memberRepository, "chat-user1@test.com", "kakao-chat-user1");
        Member user2 = createMember(memberRepository, "chat-user2@test.com", "kakao-chat-user2");
        user1Id = user1.getId();
        user2Id = user2.getId();
        user1Token = bearerToken(user1Id);
        user2Token = bearerToken(user2Id);

        // Game 생성 Controller/Service 미구현 (운영자 DB 직접 관리 가정)
        // game_id=1에 연결된 채팅방 직접 생성 후 두 회원 입장
        ChatRoomJpaEntity room = chatFixture.createRoom("테스트 게임방", "GAME", "ACTIVE", user1Id, GAME_ID);
        roomId = room.getId();
        chatRoomMemberService.join(roomId, user1Id);
        chatRoomMemberService.join(roomId, user2Id);
    }

    @Test
    @Order(1)
    @DisplayName("game 기준 채팅방 조회 — roomId 반환 확인")
    void 채팅방_조회() throws Exception {
        mockMvc.perform(get("/api/chat/rooms/game/{gameId}", GAME_ID)
                        .header("Authorization", user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomId").value(roomId));
    }

    @Test
    @Order(2)
    @DisplayName("user1 메시지 전송 — DB 저장 확인")
    void user1_메시지_전송() {
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
    @Order(3)
    @DisplayName("user2 메시지 히스토리 조회 — user1 메시지 존재 확인")
    void user2_메시지_히스토리_조회() throws Exception {
        messageService.send(
                new MessageCreateRequest(UUID.randomUUID().toString(), roomId, "TEXT", "안녕하세요!"),
                user1Id
        );

        mockMvc.perform(get("/api/chat/messages/history/{roomId}", roomId)
                        .header("Authorization", user2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(greaterThan(0)));
    }
}
