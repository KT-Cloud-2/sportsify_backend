package com.sportsify.chat.integration;

import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.application.message.dto.MessageCreateRequest;
import com.sportsify.chat.application.message.dto.MessageCreateResponse;
import com.sportsify.chat.application.message.service.MessageService;
import com.sportsify.chat.config.ChatIntegrationTestFixture;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("멤버 관리 통합 테스트")
class MemberManagementIntegrationTest {

    private static final Long OWNER_ID = 1001L;
    private static final Long MEMBER_ID = 1002L;
    private static final Long BAN_CREATOR_ID = 6001L;
    private static final Long BAN_TARGET_ID = 6002L;
    private static final int THREAD_COUNT = 50;

    @Autowired
    private ChatRoomMemberService chatRoomMemberService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepo;
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepo;
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
    @DisplayName("방장 퇴장 후 남은 멤버 채팅 정상 동작")
    void 방장_퇴장_후_남은_멤버_채팅_정상() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", OWNER_ID);
        fixture.createMember(room.getId(), OWNER_ID, "JOINED");
        fixture.createMember(room.getId(), MEMBER_ID, "JOINED");

        chatRoomMemberService.leave(room.getId(), OWNER_ID);

        ChatRoomJpaEntity updated = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("ACTIVE");

        MessageCreateRequest request =
                new MessageCreateRequest("client-msg-lifecycle-001", room.getId(), "TEXT", "방장 퇴장 후 메시지");
        MessageCreateResponse response = messageService.send(request, MEMBER_ID);

        assertThat(response.messageId()).isNotNull();
        assertThat(messageJpaRepo.existsById(response.messageId())).isTrue();
    }

    @Test
    @DisplayName("GAME 채팅방 참여")
    void GAME_채팅방_참여_JOINED_상태_추가() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", OWNER_ID);
        fixture.createMemberRecord(MEMBER_ID);

        chatRoomMemberService.join(room.getId(), MEMBER_ID);

        assertThat(chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(room.getId(), MEMBER_ID, "JOINED")).isTrue();
    }

    @Test
    @DisplayName("동일 멤버 동시 입장 중복 방지")
    void 동일_멤버_동시_입장_하나만_성공() throws InterruptedException {
        ChatRoomJpaEntity room = fixture.createRoom("동시성테스트방", "GAME", "ACTIVE", 3001L);
        long memberId = 3002L;
        fixture.createMemberRecord(memberId);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    chatRoomMemberService.join(room.getId(), memberId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - 1);
    }

    @Test
    @DisplayName("멤버 강퇴")
    void BAN_처리_후_재참여_재초대_차단() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", BAN_CREATOR_ID);
        fixture.createMember(room.getId(), BAN_CREATOR_ID, "JOINED");
        fixture.createMember(room.getId(), BAN_TARGET_ID, "JOINED");

        chatRoomMemberService.ban(room.getId(), BAN_CREATOR_ID, BAN_TARGET_ID);

        String status = chatRoomMemberJpaRepo.findByRoomIdAndMemberId(room.getId(), BAN_TARGET_ID, List.of("BANNED"))
                .orElseThrow().getStatus();
        assertThat(status).isEqualTo("BANNED");

        assertThatThrownBy(() -> chatRoomMemberService.join(room.getId(), BAN_TARGET_ID))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> chatRoomMemberService.invite(room.getId(), BAN_CREATOR_ID, BAN_TARGET_ID))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────────── 초대 ────────────────────────

    @Test
    @DisplayName("GAME 채팅방 멤버 초대")
    void GAME_채팅방_멤버_초대_INVITED_레코드_생성() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", OWNER_ID);
        fixture.createMember(room.getId(), OWNER_ID, "JOINED");
        fixture.createMemberRecord(MEMBER_ID);

        chatRoomMemberService.invite(room.getId(), OWNER_ID, MEMBER_ID);

        assertThat(chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(room.getId(), MEMBER_ID, "INVITED")).isTrue();
    }

    @Test
    @DisplayName("초대 수락")
    void 초대_수락_INVITED에서_JOINED_상태_전환() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "DIRECT", "ACTIVE", OWNER_ID);
        fixture.createMember(room.getId(), OWNER_ID, "JOINED");
        fixture.createMember(room.getId(), MEMBER_ID, "INVITED");

        chatRoomMemberService.join(room.getId(), MEMBER_ID);

        assertThat(chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(room.getId(), MEMBER_ID, "JOINED")).isTrue();
    }

    @Test
    @DisplayName("초대 거절")
    void 초대_거절_INVITED에서_REJECTED_상태_전환() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "DIRECT", "ACTIVE", OWNER_ID);
        fixture.createMember(room.getId(), OWNER_ID, "JOINED");
        fixture.createMember(room.getId(), MEMBER_ID, "INVITED");

        chatRoomMemberService.rejectInvite(MEMBER_ID, room.getId());

        String status = chatRoomMemberJpaRepo
                .findByRoomIdAndMemberId(room.getId(), MEMBER_ID, List.of("REJECT"))
                .orElseThrow().getStatus();
        assertThat(status).isEqualTo("REJECT");
    }


}
