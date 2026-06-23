#!/usr/bin/env bash
# k6 티켓팅 부하 테스트 실행 스크립트
# 사용법:
#   ./k6/ticketing/run.sh                     # 기본 100 VUS
#   ./k6/ticketing/run.sh 4000               # 4000 VUS

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_CONTAINER="${DB_CONTAINER:-sportsify-postgres}"
DB_NAME="${DB_NAME:-sportsify}"
DB_USER="${DB_USER:-sportsify}"
REDIS_CONTAINER="${REDIS_CONTAINER:-sportsify-redis}"

VUS="${1:-100}"

psql_exec() {
    docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

redis_exec() {
    docker exec "$REDIS_CONTAINER" redis-cli "$@"
}

redis_cleanup() {
    echo "▶ Redis Stream 내 데이터 및 PEL(Pending List) 일괄 소진 진행"

    local streams=("payment.completed" "ticket.opened" "game.starting" "chat.mentioned" "chat.invited")
    local group_name="notification-group"

    for stream in "${streams[@]}"; do
        # 1. 기존 메시지 데이터 전체 삭제
        redis_exec XTRIM "$stream" MAXLEN 0 > /dev/null 2>&1 || true

        # 2. XPENDING 데이터 요약본에서 ID 추출 후 일괄 XACK 처리
        local pending_ids
        pending_ids=$(redis_exec XPENDING "$stream" "$group_name" - + 1000 2>/dev/null | grep -E '^[0-9]+-[0-9]+' | awk '{print $1}' | tr '\n' ' ') || true

        # 3. 추출된 ID가 존재할 경우 단 한 번의 명령어로 일괄 ACK 처리 (락 방지)
        if [ -n "${pending_ids// /}" ]; then
            # 단일 호출 예시: redis-cli XACK stream group id1 id2 id3 ...
            redis_exec XACK "$stream" "$group_name" $pending_ids > /dev/null 2>&1 || true
        fi
    done
    echo "✔ Redis Stream 메타데이터 유지 및 잔여 백로그 소진 완료"
}

cleanup() {
    echo ""
    echo "▶ [cleanup] Redis Stream 비우기 (Consumer 처리 중단)..."
    redis_cleanup && echo "✔ Redis cleanup 완료" || echo "✘ Redis cleanup 실패"

    echo "▶ [cleanup] Consumer 소진 대기 (3초)..."
    sleep 3

    echo "▶ [cleanup] DB 초기화..."
    psql_exec < "$SCRIPT_DIR/seed_cleanup.sql" 2>/dev/null && echo "✔ DB cleanup 완료" || echo "✘ DB cleanup 실패"
}

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  🎫 Ticketing Load Test (VUS=$VUS)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "▶ [cleanup] 이전 데이터 정리..."
redis_cleanup
psql_exec < "$SCRIPT_DIR/seed_cleanup.sql" 2>/dev/null || true
echo "✔ 정리 완료"
echo ""

echo "▶ [seed] seed.sql 실행 중..."
psql_exec < "$SCRIPT_DIR/seed.sql"
echo "✔ seed 완료"
echo ""

sleep 2

echo "▶ [k6] scenario-success.js 실행 중 (VUS=$VUS)..."
k6 run -e VUS="$VUS" -e BASE_URL="$BASE_URL" "$SCRIPT_DIR/scenario-success.js"
echo ""
echo "✔ 테스트 완료"
