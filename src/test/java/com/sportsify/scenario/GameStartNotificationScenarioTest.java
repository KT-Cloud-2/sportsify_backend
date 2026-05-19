package com.sportsify.scenario;

import com.sportsify.common.notification.NotificationEventType;
import com.sportsify.common.notification.payload.GameStartPayload;
import com.sportsify.member.domain.model.Member;
import com.sportsify.notification.application.service.NotificationEventProcessor;
import com.sportsify.notification.presentation.dto.UpdateNotificationSettingRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Order(5)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[시나리오 5] 경기 시작 알림 수신")
class GameStartNotificationScenarioTest extends ScenarioTestSupport {

    @Autowired
    private NotificationEventProcessor notificationEventProcessor;
    @Autowired
    private ObjectMapper jacksonObjectMapper;

    private Long memberId;
    private String accessToken;

    @BeforeAll
    void setUpOnce() {
        Member member = createMember("game-start@test.com");
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
    @DisplayName("알림 인박스 — GAME_START 수신 확인")
    void 알림_인박스_GAME_START_수신() throws Exception {
        GameStartPayload payload = new GameStartPayload(1L, "두산 베어스", "LG 트윈스", null);
        String payloadJson = jacksonObjectMapper.writeValueAsString(payload);
        notificationEventProcessor.process(NotificationEventType.GAME_START, payloadJson);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].eventType", hasItem("GAME_START")));
    }
}
