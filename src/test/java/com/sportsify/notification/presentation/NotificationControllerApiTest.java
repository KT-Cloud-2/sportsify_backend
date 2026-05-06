package com.sportsify.notification.presentation;

import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.presentation.controller.NotificationController;
import com.sportsify.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerApiTest extends WebMvcTestSupport {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("GET /api/notifications — 인증 없이 호출 시 401 반환")
    void getNotifications_미인증_401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notifications — 인증 후 200 반환")
    void getNotifications_인증후_200() throws Exception {
        String token = bearerToken(1L, "USER");
        given(notificationService.getNotifications(any(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read — 인증 후 204 반환")
    void markRead_인증후_204() throws Exception {
        String token = bearerToken(1L, "USER");

        mockMvc.perform(patch("/api/notifications/1/read")
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/notifications/read-all — 인증 후 204 반환")
    void markAllRead_인증후_204() throws Exception {
        String token = bearerToken(1L, "USER");

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/notifications/stream — Content-Type이 text/event-stream이다")
    void subscribe_SSE_contentType() throws Exception {
        String token = bearerToken(1L, "USER");
        given(notificationService.subscribe(1L))
                .willReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/stream")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
