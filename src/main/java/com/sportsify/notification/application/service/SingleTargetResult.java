package com.sportsify.notification.application.service;

public enum SingleTargetResult {
    /** SSE 미연결 — DB 저장 완료, 즉시 ACK 필요 */
    PERSISTED,
    /** SSE 연결 — 버퍼 enqueue 완료, ACK는 flush 후 처리 */
    BUFFERED,
    /** 알림 설정 비활성화 — ACK만 처리 */
    SKIPPED,
    /** 처리 실패 — PEL 보류 */
    FAILED
}
