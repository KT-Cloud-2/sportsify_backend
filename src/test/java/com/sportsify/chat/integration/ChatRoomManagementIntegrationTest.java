package com.sportsify.chat.integration;

import com.sportsify.chat.application.chatRoom.dto.ChatRoomUpdateRequest;
import com.sportsify.chat.application.chatRoom.dto.CreateChatRoomRequest;
import com.sportsify.chat.application.chatRoom.service.ChatRoomService;
import com.sportsify.chat.application.chatRoomMember.service.ChatRoomMemberService;
import com.sportsify.chat.config.ChatIntegrationTestFixture;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaEntity;
import com.sportsify.chat.infrastructure.persistence.chatRoom.ChatRoomJpaRepository;
import com.sportsify.chat.infrastructure.persistence.chatRoomMember.ChatRoomMemberJpaRepository;
import com.sportsify.chat.infrastructure.webSocket.ChatEventPublisher;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
@DisplayName("채팅방 관리 통합 테스트")
class ChatRoomManagementIntegrationTest {

    private static final Long CREATOR_ID = 1001L;
    private static final Long MEMBER_ID = 1002L;
    private static final int THREAD_COUNT = 50;

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomMemberService chatRoomMemberService;
    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepo;
    @Autowired
    private ChatRoomMemberJpaRepository chatRoomMemberJpaRepo;
    @Autowired
    private ChatIntegrationTestFixture fixture;
    @MockitoBean
    private ChatEventPublisher chatEventPublisher;

    @AfterEach
    void tearDown() {
        fixture.deleteAll();
    }

    // ──────────────────────── 채팅방 생성 ────────────────────────

    @Test
    @DisplayName("GAME 채팅방 생성")
    void GAME_채팅방_생성_DB_저장() {
        long gameId = 42L;
        fixture.createMemberRecord(CREATOR_ID);
        CreateChatRoomRequest request =
                new CreateChatRoomRequest("GAME", "테스트 방", null, gameId, null);

        chatRoomService.create(request, CREATOR_ID);

        List<ChatRoomJpaEntity> rooms = chatRoomJpaRepo.findAll();
        assertThat(rooms).hasSize(1);
        ChatRoomJpaEntity saved = rooms.getFirst();
        assertThat(saved.getType()).isEqualTo("GAME");
        assertThat(saved.getName()).isEqualTo("테스트 방");
        assertThat(saved.getGameId()).isEqualTo(gameId);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreatedBy()).isEqualTo(CREATOR_ID);
        assertThat(chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(saved.getId(), CREATOR_ID, "JOINED")).isTrue();
    }

    @Test
    @DisplayName("DIRECT 채팅방 생성")
    void DIRECT_채팅방_생성_생성자_JOINED_초대자_INVITED() {
        fixture.createMemberRecord(CREATOR_ID);
        fixture.createMemberRecord(MEMBER_ID);
        CreateChatRoomRequest request =
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(MEMBER_ID));

        chatRoomService.create(request, CREATOR_ID);

        List<ChatRoomJpaEntity> rooms = chatRoomJpaRepo.findAll();
        assertThat(rooms).hasSize(1);
        ChatRoomJpaEntity saved = rooms.get(0);
        assertThat(saved.getType()).isEqualTo("DIRECT");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(saved.getId(), CREATOR_ID, "JOINED")).isTrue();
        assertThat(chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(saved.getId(), MEMBER_ID, "INVITED")).isTrue();
    }

    // ──────────────────────── 채팅방 수정 ────────────────────────

    @Test
    @DisplayName("채팅방 이름/이미지 수정")
    void 채팅방_이름_이미지_수정_DB_반영() {
        ChatRoomJpaEntity room = fixture.createRoom("기존 방 이름", "GAME", "ACTIVE", CREATOR_ID);

        chatRoomService.update(new ChatRoomUpdateRequest("새 방 이름", "https://new-image.com"),
                room.getId(), CREATOR_ID);

        ChatRoomJpaEntity updated = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("새 방 이름");
        assertThat(updated.getImageUrl()).isEqualTo("https://new-image.com");
    }

    // ──────────────────────── 채팅방 아카이브 / 언아카이브 ────────────────────────

    @Test
    @DisplayName("채팅방 아카이브")
    void 채팅방_아카이브_ARCHIVED_상태_저장() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", CREATOR_ID);

        chatRoomService.archive(room.getId(), CREATOR_ID);

        ChatRoomJpaEntity archived = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        assertThat(archived.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("채팅방 언아카이브")
    void 채팅방_언아카이브_ACTIVE_상태_복구() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ARCHIVED", CREATOR_ID);

        chatRoomService.unarchive(room.getId(), CREATOR_ID);

        ChatRoomJpaEntity unarchived = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        assertThat(unarchived.getStatus()).isEqualTo("ACTIVE");
    }

    // ──────────────────────── 채팅방 삭제 ────────────────────────

    @Test
    @DisplayName("채팅방 삭제")
    void 방_삭제_멤버_일괄_퇴장_원자성() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", CREATOR_ID);
        fixture.createMember(room.getId(), CREATOR_ID, "JOINED");
        fixture.createMember(room.getId(), MEMBER_ID, "JOINED");

        chatRoomService.delete(room.getId(), CREATOR_ID);

        ChatRoomJpaEntity deleted = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        List<?> activeMembers = chatRoomMemberJpaRepo.findActiveByRoomId(room.getId());

        assertThat(deleted.getStatus()).isEqualTo("DELETED");
        assertThat(activeMembers).isEmpty();
    }

    // ──────────────────────── 채팅방 상태 전환 ────────────────────────

    @Test
    @DisplayName("마지막 멤버 퇴장 시 방 EMPTY 전환")
    void 마지막_멤버_퇴장_시_방_EMPTY_전환() {
        ChatRoomJpaEntity room = fixture.createRoom("테스트방", "GAME", "ACTIVE", CREATOR_ID);
        fixture.createMember(room.getId(), CREATOR_ID, "JOINED");

        chatRoomMemberService.leave(room.getId(), CREATOR_ID);

        ChatRoomJpaEntity updated = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("EMPTY");
    }

    @Test
    @DisplayName("EMPTY 방 입장 시 ACTIVE 복구")
    void EMPTY_방_입장_시_ACTIVE_복구() {
        ChatRoomJpaEntity room = fixture.createRoom("빈방", "GAME", "EMPTY", CREATOR_ID);
        fixture.createMemberRecord(MEMBER_ID);

        chatRoomMemberService.join(room.getId(), MEMBER_ID);

        ChatRoomJpaEntity updated = chatRoomJpaRepo.findById(room.getId()).orElseThrow();
        boolean isJoined = chatRoomMemberJpaRepo
                .existsByRoomIdAndMemberIdAndStatus(room.getId(), MEMBER_ID, "JOINED");

        assertThat(updated.getStatus()).isEqualTo("ACTIVE");
        assertThat(isJoined).isTrue();
    }

    // ──────────────────────── 동시성 ────────────────────────

    @Test
    @DisplayName("DIRECT 채팅방 생성 중복 방지")
    void DIRECT_방_동시_생성_하나만_성공() throws InterruptedException {
        long creatorId = 2001L;
        long inviteeId = 2002L;
        fixture.createMemberRecord(creatorId);
        fixture.createMemberRecord(inviteeId);
        CreateChatRoomRequest request =
                new CreateChatRoomRequest("DIRECT", null, null, null, List.of(inviteeId));

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
                    chatRoomService.create(request, creatorId);
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
}
