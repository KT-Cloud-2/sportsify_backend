package com.sportsify.common.notification.payload;

public record ChatMentionPayload(
        Long memberId,
        Long roomId,
        String roomName,
        Long senderId,
        String senderName,
        String message
) implements NotificationPayload {

    private static final int PREVIEW_MAX_LENGTH = 15;

    public static ChatMentionPayload ofText(Long memberId, Long roomId, String roomName, Long senderId, String senderName, String message) {
        return new ChatMentionPayload(memberId, roomId, roomName, senderId, senderName, message.length() > PREVIEW_MAX_LENGTH
                ? message.substring(0, PREVIEW_MAX_LENGTH) + "..."
                : message);
    }

    public static ChatMentionPayload ofImage(Long memberId, Long roomId, String roomName, Long senderId, String senderName) {
        return new ChatMentionPayload(memberId, roomId, roomName, senderId, senderName, "(이미지 포함)");
    }

    public static ChatMentionPayload ofFile(Long memberId, Long roomId, String roomName, Long senderId, String senderName) {
        return new ChatMentionPayload(memberId, roomId, roomName, senderId, senderName, "(파일 포함)");
    }
}
