package com.sportsify.chat.domain.model.event;

public enum EventType {
    TYPING(null),
    MESSAGE_SENT(null),
    MESSAGE_DELETED(null),
    READ_RECEIPT(null),
    REPLAY_MESSAGE(null),
    REPLAY_OVERFLOW(null),
    MEMBER_REJECTED("%s 님이 초대를 거절했습니다."),
    MEMBER_JOINED("%s 님이 채팅방에 참여했습니다."),
    MEMBER_LEFT("%s 님이 채팅방을 나갔습니다."),
    MEMBER_INVITED("%s 님을 초대했습니다."),
    MEMBER_BANNED("%s 님이 강퇴되었습니다."),
    ROOM_UPDATED("채팅방 정보가 변경되었습니다."),
    ROOM_DELETED("채팅방이 삭제되었습니다."),
    ROOM_ARCHIVED("채팅방이 보관되었습니다."),
    ROOM_UNARCHIVED("채팅방 보관이 해제되었습니다.");

    private final String alertTemplate;

    EventType(String alertTemplate) {
        this.alertTemplate = alertTemplate;
    }

    public String formatAlert(String userId) {
        if (alertTemplate == null) return null;
        return userId != null ? String.format(alertTemplate, userId) : alertTemplate;
    }
}
