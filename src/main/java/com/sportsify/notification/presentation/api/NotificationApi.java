package com.sportsify.notification.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.notification.presentation.dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "알림", description = "알림 API")
@AuthRequiredApi
@CommonApiResponses
public interface NotificationApi {

    // ── 인박스 ──

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

    // ── 설정 ──

    @SwaggerApi(summary = "알림 설정 조회", error = NOTIFICATION_SETTING_NOT_FOUND)
    ResponseEntity<NotificationSettingResponse> getSetting(Long memberId);

    @SwaggerApi(summary = "알림 설정 수정", error = NOTIFICATION_SETTING_NOT_FOUND)
    ResponseEntity<NotificationSettingResponse> updateSetting(Long memberId, @RequestBody UpdateNotificationSettingRequest request);

    // ── 채널 ──

    @SwaggerApi(summary = "채널 목록 조회")
    ResponseEntity<List<NotificationChannelResponse>> getChannels(Long memberId);

    @SwaggerApi(summary = "채널 등록", error = NOTIFICATION_CHANNEL_ALREADY_EXISTS)
    ResponseEntity<NotificationChannelResponse> registerChannel(Long memberId, @RequestBody RegisterChannelRequest request);

    @SwaggerApi(summary = "채널 삭제", responseCode = "204", responseDescription = "성공 (본문 없음)",
            error = NOTIFICATION_CHANNEL_NOT_FOUND)
    ResponseEntity<Void> deleteChannel(Long memberId, Long channelId);

    @SwaggerApi(summary = "채널 활성화/비활성화 토글", error = NOTIFICATION_CHANNEL_NOT_FOUND)
    ResponseEntity<NotificationChannelResponse> toggleChannel(Long memberId, Long channelId);
}
