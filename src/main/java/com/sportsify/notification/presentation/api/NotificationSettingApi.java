package com.sportsify.notification.presentation.api;

import com.sportsify.common.swagger.AuthRequiredApi;
import com.sportsify.common.swagger.CommonApiResponses;
import com.sportsify.common.swagger.SwaggerApi;
import com.sportsify.notification.presentation.dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.sportsify.common.exception.ErrorCode.*;

@Tag(name = "NotificationSetting", description = "알림 설정 API")
@AuthRequiredApi
@CommonApiResponses
public interface NotificationSettingApi {

    @SwaggerApi(summary = "알림 설정 조회", error = NOTIFICATION_SETTING_NOT_FOUND)
    ResponseEntity<NotificationSettingResponse> getSetting(Long memberId);

    @SwaggerApi(summary = "알림 설정 수정", error = NOTIFICATION_SETTING_NOT_FOUND)
    ResponseEntity<NotificationSettingResponse> updateSetting(Long memberId, @RequestBody UpdateNotificationSettingRequest request);

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
