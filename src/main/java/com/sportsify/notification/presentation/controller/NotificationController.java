package com.sportsify.notification.presentation.controller;

import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.application.service.NotificationSettingService;
import com.sportsify.notification.presentation.api.NotificationApi;
import com.sportsify.notification.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

    // ── 인박스 ──

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                notificationService.getNotifications(memberId, pageable).map(NotificationResponse::from)
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long notificationId
    ) {
        notificationService.markRead(memberId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long memberId) {
        notificationService.markAllRead(memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long memberId) {
        return notificationService.subscribe(memberId);
    }

    // ── 설정 ──

    @GetMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> getSetting(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(NotificationSettingResponse.from(notificationSettingService.getSetting(memberId)));
    }

    @PutMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> updateSetting(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody UpdateNotificationSettingRequest request
    ) {
        return ResponseEntity.ok(NotificationSettingResponse.from(
                notificationSettingService.updateSetting(
                        memberId,
                        request.ticketOpenAlert(),
                        request.gameStartAlert(),
                        request.paymentAlert(),
                        request.chatMentionAlert()
                )
        ));
    }

    // ── 채널 ──

    @GetMapping("/channels")
    public ResponseEntity<List<NotificationChannelResponse>> getChannels(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(
                notificationSettingService.getChannels(memberId).stream()
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
                notificationSettingService.registerChannel(memberId, request.channelType(), request.channelTarget())
        ));
    }

    @DeleteMapping("/channels/{channelId}")
    public ResponseEntity<Void> deleteChannel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId
    ) {
        notificationSettingService.deleteChannel(memberId, channelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/channels/{channelId}/toggle")
    public ResponseEntity<NotificationChannelResponse> toggleChannel(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long channelId
    ) {
        return ResponseEntity.ok(NotificationChannelResponse.from(
                notificationSettingService.toggleChannel(memberId, channelId)
        ));
    }
}
