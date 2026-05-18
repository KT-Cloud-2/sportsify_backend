package com.sportsify.game.domain.model;

public enum GameStatus {
    SCHEDULED,      // 경기 예정 (판매 전)
    ON_SALE,        // 티켓 판매 중
    SALE_CLOSED,    // 판매 마감
    IN_PROGRESS,    // 경기 진행 중
    FINISHED,       // 경기 종료
    CANCELLED       // 경기 취소
}