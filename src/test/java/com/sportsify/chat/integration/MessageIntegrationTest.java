package com.sportsify.chat.integration;

import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.dto.MessageCreateResponse;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.config.ChatIntegrationTestFixture;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.config.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("메시지 통합 테스트")
class MessageIntegrationTest {

    private static final Long SENDER_ID = 4001L;
    private static final Long DELETE_SENDER_ID = 7001L;
    private static final Long OTHER_MEMBER_ID = 7002L;
    private static final Long DELETE_ROOM_CREATOR_ID = 7003L;

    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageJpaRepository messageJpaRepo;
    @Autowired
    private ChatIntegrationTestFixture fixture;
    @MockitoBean
    private ChatEventPublisher chatEventPublisher;

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    @Test
    @DisplayName("메시지 전송")
    void 메시지_전송_성공_DB_저장() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", SENDER_ID);
        fixture.createMember(room.getId(), SENDER_ID, "JOINED");

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-001", room.getId(), "TEXT", "안녕하세요");
        MessageCreateResponse response = messageService.send(request, SENDER_ID);

        assertThat(response.messageId()).isNotNull();
        assertThat(messageJpaRepo.existsById(response.messageId())).isTrue();
    }

    @Test
    @DisplayName("아카이브된 채팅방에 메시지 전송 시도")
    void ARCHIVED_방_메시지_전송_실패() {
        ChatRoomJpaEntity room = fixture.createRoom("아카이브방", "GAME", "ARCHIVED", SENDER_ID);
        fixture.createMember(room.getId(), SENDER_ID, "JOINED");

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-002", room.getId(), "TEXT", "메시지");

        assertThatThrownBy(() -> messageService.send(request, SENDER_ID))
                .isInstanceOf(BusinessException.class);
        assertThat(messageJpaRepo.countAll(room.getId())).isZero();
    }

    @Test
    @DisplayName("BAN 상태 사용자의 메시지 전송 시도")
    void BANNED_멤버_메시지_전송_실패() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", 4002L);
        fixture.createMember(room.getId(), SENDER_ID, "BANNED");

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-003", room.getId(), "TEXT", "메시지");

        assertThatThrownBy(() -> messageService.send(request, SENDER_ID))
                .isInstanceOf(BusinessException.class);
        assertThat(messageJpaRepo.countAll(room.getId())).isZero();
    }

    @Test
    @DisplayName("메시지 삭제")
    void 메시지_삭제_후_DB_반영_및_재삭제_예외() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", DELETE_ROOM_CREATOR_ID);
        fixture.createMember(room.getId(), DELETE_SENDER_ID, "JOINED");
        MessageJpaEntity message = fixture.createMessage(room.getId(), DELETE_SENDER_ID);

        messageService.delete(message.getId(), DELETE_SENDER_ID);

        MessageJpaEntity deleted = messageJpaRepo.findById(message.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo("DELETED");

        assertThatThrownBy(() -> messageService.delete(message.getId(), DELETE_SENDER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("타인의 메시지 삭제 시도")
    void 타인_메시지_삭제_FORBIDDEN_예외() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", DELETE_ROOM_CREATOR_ID);
        fixture.createMember(room.getId(), OTHER_MEMBER_ID, "JOINED");
        MessageJpaEntity message = fixture.createMessage(room.getId(), DELETE_SENDER_ID);

        assertThatThrownBy(() -> messageService.delete(message.getId(), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class);

        MessageJpaEntity unchanged = messageJpaRepo.findById(message.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo("ACTIVE");
    }
}
