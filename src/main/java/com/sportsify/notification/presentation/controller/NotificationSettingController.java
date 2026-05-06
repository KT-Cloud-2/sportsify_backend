package com.sportsify.notification.presentation.controller;

import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.presentation.api.NotificationSettingApi;
import com.sportsify.notification.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSettingController implements NotificationSettingApi {

    private final NotificationSettingService settingService;

    @GetMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> getSetting(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(NotificationSettingResponse.from(settingService.getSetting(memberId)));
    }

    @PutMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> updateSetting(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateNotificationSettingRequest request
    ) {
        return ResponseEntity.ok(NotificationSettingResponse.from(
                settingService.updateSetting(memberId, request.ticketOpenAlert(), request.gameStartAlert(), request.paymentAlert())
        ));
    }

    @GetMapping("/channels")
    public ResponseEntity<List<NotificationChannelResponse>> getChannels(
            @AuthenticationPrincipal Long memberId
    ) {
        return ResponseEntity.ok(
                settingService.getChannels(memberId).stream()
                        .map(NotificationChannelResponse::from)
                        .toList()
        );
    }

    @PostMapping("/channels")
    public ResponseEntity<NotificationChannelResponse> registerChannel(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RegisterChannelRequest request
    ) {
        return ResponseEntity.ok(NotificationChannelResponse.from(
                settingService.registerChannel(memberId, request.channelType(), request.channelTarget())
        ));
    }

    @DeleteMapping("/channels/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId
    ) {
        settingService.deleteChannel(memberId, channelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/channels/{channelId}/toggle")
    public ResponseEntity<NotificationChannelResponse> toggleChannel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId
    ) {
        return ResponseEntity.ok(NotificationChannelResponse.from(
                settingService.toggleChannel(memberId, channelId)
        ));
    }
}
