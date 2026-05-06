package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomTest {

    private static final MemberId CREATOR = MemberId.of(1L);
    private static final GameId GAME_ID = GameId.of(5L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 4, 12, 0);
    private static final LocalDateTime LATER = NOW.plusHours(1);

    // ──────────────────────── create ────────────────────────

    @Test
    @DisplayName("GAME 타입 채팅방을 생성하면 ACTIVE 상태로 초기화된다")
    void create_GAME타입_초기상태() {
        ChatRoom room = ChatRoom.create(
                ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME, "https://img.png", GAME_ID, CREATOR, NOW);

        assertThat(room.getName()).isEqualTo(ChatRoomName.of("한화 VS LG"));
        assertThat(room.getType()).isEqualTo(ChatRoomType.GAME);
        assertThat(room.getGameId()).isEqualTo(GAME_ID);
        assertThat(room.getImageUrl()).isEqualTo("https://img.png");
        assertThat(room.getCreatedBy()).isEqualTo(CREATOR);
        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(room.getCreatedAt()).isEqualTo(NOW);
        assertThat(room.getId()).isNull();
    }

    @Test
    @DisplayName("DIRECT 타입 채팅방을 생성하면 gameId가 null이다")
    void create_DIRECT타입_gameId_null() {
        ChatRoom room = ChatRoom.create(
                ChatRoomName.of("DM"), ChatRoomType.DIRECT, null, null, CREATOR, NOW);

        assertThat(room.getType()).isEqualTo(ChatRoomType.DIRECT);
        assertThat(room.getGameId()).isNull();
        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    // ──────────────────────── assignId ────────────────────────

    @Test
    @DisplayName("새 채팅방에 id를 부여한다")
    void assignId_부여성공() {
        ChatRoom room = ChatRoom.create(
                ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME, null, GAME_ID, CREATOR, NOW);

        room.assignId(ChatRoomId.of(10L));

        assertThat(room.getId()).isEqualTo(ChatRoomId.of(10L));
    }

    // ──────────────────────── rename ────────────────────────

    @Test
    @DisplayName("방장이 채팅방 이름을 변경하면 name과 updatedAt이 갱신된다")
    void rename_이름변경() {
        ChatRoom room = activeGameRoom();

        room.rename(ChatRoomName.of("새로운 방 이름"), LATER, CREATOR);

        assertThat(room.getName()).isEqualTo(ChatRoomName.of("새로운 방 이름"));
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("동일한 이름으로 변경하면 updatedAt이 갱신되지 않는다")
    void rename_동일이름_멱등() {
        ChatRoom room = activeGameRoom();

        room.rename(ChatRoomName.of("한화 VS LG"), LATER, CREATOR);

        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── changeImage ────────────────────────

    @Test
    @DisplayName("방장이 이미지를 변경하면 imageUrl과 updatedAt이 갱신된다")
    void changeImage_이미지변경() {
        ChatRoom room = activeGameRoom();

        room.changeImage("https://new.png", LATER, CREATOR);

        assertThat(room.getImageUrl()).isEqualTo("https://new.png");
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("동일한 이미지로 변경하면 updatedAt이 갱신되지 않는다")
    void changeImage_동일이미지_멱등() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                "https://img.png", GAME_ID, NOW, NOW, ChatRoomStatus.ACTIVE, CREATOR);

        room.changeImage("https://img.png", LATER, CREATOR);

        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("빈 문자열로 이미지를 변경하면 imageUrl이 null로 변경된다")
    void changeImage_빈문자열_null처리() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                "https://img.png", GAME_ID, NOW, NOW, ChatRoomStatus.ACTIVE, CREATOR);

        room.changeImage(null, LATER, CREATOR);

        assertThat(room.getImageUrl()).isNull();
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    // ──────────────────────── archive ────────────────────────

    @Test
    @DisplayName("ACTIVE 채팅방을 아카이브하면 ARCHIVED 상태로 변경된다")
    void archive_상태변경() {
        ChatRoom room = activeGameRoom();

        room.archive(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ARCHIVED);
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 ARCHIVED 채팅방을 아카이브해도 updatedAt이 갱신되지 않는다")
    void archive_ARCHIVED_멱등() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        room.archive(LATER);

        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── delete ────────────────────────

    @Test
    @DisplayName("방장이 채팅방을 삭제하면 DELETED 상태로 변경된다")
    void delete_상태변경() {
        ChatRoom room = activeGameRoom();

        room.delete(LATER, CREATOR);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.DELETED);
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 DELETED 채팅방을 삭제해도 updatedAt이 갱신되지 않는다")
    void delete_DELETED_멱등() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.DELETED, CREATOR);

        room.delete(LATER, CREATOR);

        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoom activeGameRoom() {
        return ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ACTIVE, CREATOR);
    }
}
