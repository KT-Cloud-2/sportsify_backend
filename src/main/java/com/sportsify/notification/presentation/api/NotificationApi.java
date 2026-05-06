package com.sportsify.notification.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.notification.presentation.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "Notification", description = "알림 인박스 API")
@AuthRequiredApi
@CommonApiResponses
public interface NotificationApi {

    @SwaggerApi(summary = "알림 목록 조회", description = "최신순 페이징 반환.")
    ResponseEntity<Page<NotificationResponse>> getNotifications(Long memberId, Pageable pageable);

    @SwaggerApi(summary = "알림 읽음 처리", description = "단건 읽음 처리.",
            errors = {NOTIFICATION_NOT_FOUND, NOTIFICATION_ALREADY_READ})
    ResponseEntity<Void> markRead(Long memberId, Long notificationId);

    @SwaggerApi(summary = "전체 읽음 처리", description = "미읽음 알림 전체 읽음 처리.",
            responseCode = "204", responseDescription = "성공 (본문 없음)")
    ResponseEntity<Void> markAllRead(Long memberId);

    @SwaggerApi(summary = "SSE 연결", description = "실시간 알림 수신용 SSE 스트림.")
    SseEmitter subscribe(Long memberId);
}
