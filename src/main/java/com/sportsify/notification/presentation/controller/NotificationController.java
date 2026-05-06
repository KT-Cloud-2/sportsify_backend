package com.sportsify.notification.presentation.controller;

import com.sportsify.notification.application.service.NotificationService;
import com.sportsify.notification.presentation.api.NotificationApi;
import com.sportsify.notification.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long memberId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                notificationService.getNotifications(memberId, pageable)
                        .map(NotificationResponse::from)
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
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal Long memberId
    ) {
        notificationService.markAllRead(memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal Long memberId
    ) {
        return notificationService.subscribe(memberId);
    }
}
