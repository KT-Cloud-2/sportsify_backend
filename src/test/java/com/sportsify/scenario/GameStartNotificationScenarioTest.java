package com.sportsify.scenario;

import com.sportsify.common.notification.NotificationEventPublisher;
import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.GameStartPayload;
import com.sportsify.member.domain.model.Member;
import com.sportsify.member.infrastructure.repository.MemberJpaRepository;
import com.sportsify.notification.presentation.dto.UpdateNotificationSettingRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(5)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 5] 경기 시작 알림 수신")
class GameStartNotificationScenarioTest extends ScenarioTestSupport {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private NotificationEventPublisher notificationEventPublisher;

    private static Long memberId;
    private static String accessToken;

    @BeforeEach
    void setUpMember() {
        Member member = createMember(memberRepository, "game-start@test.com", "kakao-game-start-001");
        memberId = member.getId();
        accessToken = bearerToken(memberId);
    }

    @Test
    @Order(1)
    @DisplayName("알림 설정 — gameStartAlert ON")
    void 알림_설정_gameStartAlert_ON() throws Exception {
        UpdateNotificationSettingRequest request = new UpdateNotificationSettingRequest(
                false, true, false, false
        );

        mockMvc.perform(put("/api/notifications/settings")
                        .header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("GAME_START 이벤트 발행 → Redis Stream 적재")
    void GAME_START_이벤트_발행() {
        notificationEventPublisher.publish(
                NotificationEventType.GAME_START,
                new GameStartPayload(
                        1L,
                        "두산 베어스",
                        "LG 트윈스",
                        LocalDateTime.now().plusHours(1)
                )
        );
    }

    @Test
    @Order(3)
    @DisplayName("알림 인박스 — GAME_START 수신 확인 (Awaitility 5s)")
    void 알림_인박스_GAME_START_수신() {
        Awaitility.await()
                .atMost(5, SECONDS)
                .untilAsserted(() ->
                        mockMvc.perform(get("/api/notifications")
                                        .header("Authorization", accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[*].eventType",
                                        hasItem("GAME_START")))
                );
    }
}
