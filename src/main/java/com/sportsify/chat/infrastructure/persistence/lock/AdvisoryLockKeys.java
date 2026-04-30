package com.sportsify.chat.infrastructure.persistence.lock;


import com.sportsify.chat.domain.model.chatRoom.ChatRoomId;
import com.sportsify.chat.domain.model.chatRoom.MemberId;

/**
 * Advisory Lock 키의 표준 네이밍을 한 곳에 모아둔 헬퍼.
 */
public final class AdvisoryLockKeys {

    private AdvisoryLockKeys() {
    }

    /**
     * 두 멤버 ID 순서를 정규화해 같은 쌍이 항상 같은 키 생성.
     */
    public static String directRoomCreationForDm(MemberId a, MemberId b) {
        long min = Math.min(a.value(), b.value());
        long max = Math.max(a.value(), b.value());
        return "chat:dm:create:" + min + ":" + max;
    }

    public static String directRoomCreationForMemberJoin(ChatRoomId a, MemberId b) {
        return "join:" + a.value() + ":" + b.value();
    }
}