package com.sportsify.chat.integration;

import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.dto.MessageCreateResponse;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.message.MessageJpaRepository;
import com.sportsify.common.exception.BusinessException;
import com.sportsify.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [통합] 메시지 전송 상태/권한 검증 통합 테스트
 *
 * 테스트 가치: 권한/상태 검증 + 트랜잭션
 * - SELECT FOR UPDATE를 통한 방 상태·멤버 상태 검증이 실제 DB에서 동작하는지 검증
 * - 예외 발생 시 트랜잭션 롤백으로 메시지가 저장되지 않는지 확인
 *
 * 단위 테스트와의 차이:
 * - 단위 테스트는 Mock으로 방/멤버 상태를 주입해 분기 로직만 검증
 * - 통합 테스트는 실제 DB의 SELECT FOR UPDATE 동작과 롤백 여부를 검증
 */
@DisplayName("[통합] 메시지 전송 상태/권한 검증 통합 테스트")
class MessageStateIntegrationTest extends RepositoryTestSupport {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageJpaRepository messageJpaRepo;

    @Autowired
    private ChatIntegrationTestFixture fixture;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Long SENDER_ID = 4001L;

    /**
     * 왜 통합 테스트가 필요한가:
     * - send()는 SELECT FOR UPDATE(방 조회) → SELECT FOR UPDATE(멤버 조회) → INSERT(메시지)를
     *   단일 트랜잭션으로 처리하며, 전체 흐름이 실제 DB에서 정상 동작하는지 확인
     *
     * 실패 가능 포인트:
     * - SELECT FOR UPDATE가 ACTIVE 방을 찾지 못하면 NOT_FOUND 예외 발생
     * - 멤버 상태 조회에서 JOINED가 아니면 FORBIDDEN 예외 발생
     */
    @Test
    @DisplayName("ACTIVE 방에서 JOINED 멤버의 메시지 전송이 DB에 저장된다")
    void 메시지_전송_성공_DB_저장() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", SENDER_ID);
        fixture.createMember(room.getId(), SENDER_ID, "JOINED");
        entityManager.flush();
        entityManager.clear();

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-001", room.getId(), "TEXT", "안녕하세요");

        // When
        MessageCreateResponse response = messageService.send(request, SENDER_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(response.messageId()).isNotNull();
        assertThat(messageJpaRepo.existsById(response.messageId())).isTrue();
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - send()에서 findByIdForUpdateWrite(SELECT FOR UPDATE)로 조회한 방의 실제 DB 상태가
     *   ARCHIVED일 때 예외를 던지고 트랜잭션이 롤백되는지 검증
     *
     * 실패 가능 포인트:
     * - ARCHIVED 상태 체크 전에 메시지가 저장되거나
     *   예외 발생 후에도 메시지가 커밋되면 데이터 정합성 오류 발생
     */
    @Test
    @DisplayName("ARCHIVED 방에서 메시지 전송 시 예외가 발생하고 메시지가 저장되지 않는다")
    void ARCHIVED_방_메시지_전송_실패() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("아카이브방", "GAME", "ARCHIVED", SENDER_ID);
        fixture.createMember(room.getId(), SENDER_ID, "JOINED");
        entityManager.flush();
        entityManager.clear();

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-002", room.getId(), "TEXT", "메시지");

        // When & Then
        assertThatThrownBy(() -> messageService.send(request, SENDER_ID))
                .isInstanceOf(BusinessException.class);

        entityManager.flush();
        entityManager.clear();
        assertThat(messageJpaRepo.countAll(room.getId())).isZero();
    }

    /**
     * 왜 통합 테스트가 필요한가:
     * - send()에서 findByRoomAndMemberForUpdate(SELECT FOR UPDATE)로 조회한 멤버의
     *   실제 DB 상태가 BANNED일 때 예외를 던지고 트랜잭션이 롤백되는지 검증
     *
     * 실패 가능 포인트:
     * - 멤버 상태 체크에 SELECT FOR UPDATE가 없으면 BAN 처리 직후 동시 요청이 통과될 수 있음
     * - 예외 발생 후 롤백이 누락되면 BANNED 사용자의 메시지가 DB에 저장됨
     */
    @Test
    @DisplayName("BANNED 멤버가 메시지 전송 시 예외가 발생하고 메시지가 저장되지 않는다")
    void BANNED_멤버_메시지_전송_실패() {
        // Given
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", 4002L);
        fixture.createMember(room.getId(), SENDER_ID, "BANNED");
        entityManager.flush();
        entityManager.clear();

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-003", room.getId(), "TEXT", "메시지");

        // When & Then
        assertThatThrownBy(() -> messageService.send(request, SENDER_ID))
                .isInstanceOf(BusinessException.class);

        entityManager.flush();
        entityManager.clear();
        assertThat(messageJpaRepo.countAll(room.getId())).isZero();
    }
}
