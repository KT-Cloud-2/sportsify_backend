package com.sportsify.game.application;

import com.sportsify.game.application.scheduler.GameSaleTaskScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameSaleTaskSchedulerTest {

    @InjectMocks
    private GameSaleTaskScheduler gameSaleTaskScheduler;

    @Mock
    private TaskScheduler gameTaskScheduler;


    @Test
    @DisplayName("scheduleSaleStart 호출 시 TaskScheduler에 정확한 시각으로 등록된다.")
    void scheduleSaleStart_registersCorrectInstant() {
        LocalDateTime saleStartAt = LocalDateTime.of(2025, 5, 1, 10, 0);
        Instant expectedInstant = saleStartAt.atZone(ZoneId.systemDefault()).toInstant();

        gameSaleTaskScheduler.scheduleSaleStart(1L, saleStartAt);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(gameTaskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isEqualTo(expectedInstant);
    }

    @Test
    @DisplayName("scheduleSaleEnd 호출 시 TaskScheduler에 정확한 시각으로 등록된다.")
    void scheduleSaleEnd_registersCorrectInstant() {
        LocalDateTime saleEndAt = LocalDateTime.of(2025, 5, 1, 18, 0);
        Instant expectedInstant = saleEndAt.atZone(ZoneId.systemDefault()).toInstant();

        gameSaleTaskScheduler.scheduleSaleEnd(1L, saleEndAt);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(gameTaskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isEqualTo(expectedInstant);
    }
}
