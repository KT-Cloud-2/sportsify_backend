package com.sportsify.chat.domain;

import com.sportsify.chat.domain.model.chatRoom.*;
import com.sportsify.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomTest {

    private static final MemberId CREATOR = MemberId.of(1L);
    private static final MemberId OTHER = MemberId.of(99L);
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

    @Test
    @DisplayName("GAME 타입으로 생성 시 gameId가 null이면 예외를 던진다")
    void create_GAME타입_gameId_null_예외() {
        assertThatThrownBy(() -> ChatRoom.create(
                ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME, null, null, CREATOR, NOW))
                .isInstanceOf(IllegalArgumentException.class);
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

    @Test
    @DisplayName("이미 id가 부여된 채팅방에 재부여하면 예외가 발생한다")
    void assignId_중복_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ACTIVE, CREATOR);

        assertThatThrownBy(() -> room.assignId(ChatRoomId.of(2L)))
                .isInstanceOf(BusinessException.class);
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

    @Test
    @DisplayName("방장이 아닌 멤버가 이름을 변경하면 예외가 발생한다")
    void rename_비방장_예외() {
        ChatRoom room = activeGameRoom();

        assertThatThrownBy(() -> room.rename(ChatRoomName.of("새 이름"), LATER, OTHER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("ARCHIVED 채팅방에서 이름을 변경하면 예외가 발생한다")
    void rename_ARCHIVED방_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        assertThatThrownBy(() -> room.rename(ChatRoomName.of("새 이름"), LATER, CREATOR))
                .isInstanceOf(BusinessException.class);
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
    @DisplayName("null로 이미지를 변경하면 imageUrl이 null로 변경된다")
    void changeImage_빈문자열_null처리() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                "https://img.png", GAME_ID, NOW, NOW, ChatRoomStatus.ACTIVE, CREATOR);

        room.changeImage(null, LATER, CREATOR);

        assertThat(room.getImageUrl()).isNull();
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("방장이 아닌 멤버가 이미지를 변경하면 예외가 발생한다")
    void changeImage_비방장_예외() {
        ChatRoom room = activeGameRoom();

        assertThatThrownBy(() -> room.changeImage("https://new.png", LATER, OTHER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("ARCHIVED 채팅방에서 이미지를 변경하면 예외가 발생한다")
    void changeImage_ARCHIVED방_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        assertThatThrownBy(() -> room.changeImage("https://new.png", LATER, CREATOR))
                .isInstanceOf(BusinessException.class);
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
    @DisplayName("이미 ARCHIVED 채팅방을 다시 아카이브하면 예외가 발생한다")
    void archive_ARCHIVED_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        assertThatThrownBy(() -> room.archive(LATER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("DELETED 채팅방을 아카이브하면 예외가 발생한다")
    void archive_DELETED방_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.DELETED, CREATOR);

        assertThatThrownBy(() -> room.archive(LATER))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── unarchive ────────────────────────

    @Test
    @DisplayName("ARCHIVED 채팅방을 unarchive하면 ACTIVE 상태로 복원된다")
    void unarchive_ARCHIVED에서ACTIVE복원() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        room.unarchive(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 ACTIVE인 채팅방에 unarchive를 호출해도 상태가 변경되지 않는다")
    void unarchive_ACTIVE() {
        ChatRoom room = activeGameRoom();

        room.unarchive(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("EMPTY 채팅방에 unarchive를 호출해도 상태가 변경되지 않는다")
    void unarchive_EMPTY() {
        ChatRoom room = emptyGameRoom();

        room.unarchive(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.EMPTY);
        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("DELETED 채팅방을 unarchive하면 예외가 발생한다")
    void unarchive_DELETED_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.DELETED, CREATOR);

        assertThatThrownBy(() -> room.unarchive(LATER))
                .isInstanceOf(BusinessException.class);
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


        assertThatThrownBy(() -> room.delete(LATER, CREATOR))
                .isInstanceOf(BusinessException.class);

        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("방장이 아닌 멤버가 채팅방을 삭제하면 예외가 발생한다")
    void delete_비방장_예외() {
        ChatRoom room = activeGameRoom();

        assertThatThrownBy(() -> room.delete(LATER, OTHER))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── markEmpty ────────────────────────

    @Test
    @DisplayName("ACTIVE 채팅방에서 마지막 멤버 퇴장 시 EMPTY 상태로 전환된다")
    void markEmpty_ACTIVE에서EMPTY전환() {
        ChatRoom room = activeGameRoom();

        room.markEmpty(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.EMPTY);
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("이미 EMPTY인 채팅방에 markEmpty를 호출해도 updatedAt이 갱신되지 않는다")
    void markEmpty_EMPTY_멱등() {
        ChatRoom room = emptyGameRoom();

        room.markEmpty(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.EMPTY);
        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("DELETED 채팅방에 markEmpty를 호출하면 예외가 발생한다")
    void markEmpty_DELETED_예외() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.DELETED, CREATOR);

        assertThatThrownBy(() -> room.markEmpty(LATER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("ARCHIVED 채팅방에 markEmpty를 호출해도 상태가 변경되지 않는다")
    void markEmpty_ARCHIVED_노옵() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        room.markEmpty(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ARCHIVED);
        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── reactivate ────────────────────────

    @Test
    @DisplayName("EMPTY 채팅방에 첫 멤버가 입장하면 ACTIVE 상태로 복원된다")
    void reactivate_EMPTY에서ACTIVE복원() {
        ChatRoom room = emptyGameRoom();

        room.reactivate(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("EMPTY가 아닌 채팅방에 reactivate를 호출해도 상태가 변경되지 않는다")
    void reactivate_EMPTY아님_노옵() {
        ChatRoom room = activeGameRoom();

        room.reactivate(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("ARCHIVED 채팅방에 reactivate를 호출해도 상태가 변경되지 않는다")
    void reactivate_ARCHIVED_노옵() {
        ChatRoom room = ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ARCHIVED, CREATOR);

        room.reactivate(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ARCHIVED);
        assertThat(room.getUpdatedAt()).isEqualTo(NOW);
    }

    // ──────────────────────── EMPTY 상태에서의 다른 동작 ────────────────────────

    @Test
    @DisplayName("EMPTY 채팅방도 아카이브할 수 있다")
    void archive_EMPTY방_아카이브가능() {
        ChatRoom room = emptyGameRoom();

        room.archive(LATER);

        assertThat(room.getStatus()).isEqualTo(ChatRoomStatus.ARCHIVED);
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("EMPTY 채팅방에서도 방장이 이름을 변경할 수 있다")
    void rename_EMPTY방_이름변경가능() {
        ChatRoom room = emptyGameRoom();

        room.rename(ChatRoomName.of("새 이름"), LATER, CREATOR);

        assertThat(room.getName()).isEqualTo(ChatRoomName.of("새 이름"));
        assertThat(room.getUpdatedAt()).isEqualTo(LATER);
    }

    // ──────────────────────── 픽스처 헬퍼 ────────────────────────

    private ChatRoom activeGameRoom() {
        return ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.ACTIVE, CREATOR);
    }

    private ChatRoom emptyGameRoom() {
        return ChatRoom.restore(
                ChatRoomId.of(1L), ChatRoomName.of("한화 VS LG"), ChatRoomType.GAME,
                null, GAME_ID, NOW, NOW, ChatRoomStatus.EMPTY, CREATOR);
    }
}
