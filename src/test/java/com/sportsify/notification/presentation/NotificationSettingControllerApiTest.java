package com.sportsify.notification.presentation;

import com.sportsify.notification.application.dto.NotificationSettingResult;
import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.presentation.controller.NotificationController;
import com.sportsify.notification.presentation.controller.NotificationSettingController;
import com.sportsify.notification.presentation.dto.UpdateNotificationSettingRequest;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({NotificationController.class, NotificationSettingController.class})
class NotificationSettingControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("PUT /api/v1/notifications/settings — 요청 바디 없으면 400 반환")
    void updateSetting_바디없음_400() throws Exception {
        String token = bearerToken(1L, "USER");

        mockMvc.perform(put("/api/v1/notifications/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/settings — 정상 요청 시 200 반환")
    void updateSetting_정상요청_200() throws Exception {
        String token = bearerToken(1L, "USER");
        given(notificationSettingService.updateSetting(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .willReturn(new NotificationSettingResult(true, false, true));

        mockMvc.perform(put("/api/v1/notifications/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateNotificationSettingRequest(true, false, true)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketOpenAlert").value(true))
                .andExpect(jsonPath("$.gameStartAlert").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/settings — 미인증 시 401 반환")
    void getSetting_미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/channels — 미인증 시 401 반환")
    void getChannels_미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/channels"))
                .andExpect(status().isUnauthorized());
    }
}
