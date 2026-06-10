#!/usr/bin/env bash
# k6 부하 테스트 실행 스크립트
# 사용법:
#   ./k6/chat/run.sh                          # ramping + constant 순차 실행
#   ./k6/chat/run.sh ramping                  # ramping.js 만 실행
#   ./k6/chat/run.sh constant                 # constant.js 만 실행
#   ./k6/chat/run.sh constant --noCleanUp     # 테스트 후 seed 데이터 유지 (디버깅용)
#
# 환경 변수 (선택):
#   BASE_URL      — 테스트 대상 서버          (기본: http://localhost:8080)
#   DB_CONTAINER  — PostgreSQL 컨테이너 이름  (기본: sportsify-postgres)
#   DB_NAME       — 데이터베이스 이름         (기본: sportsify)
#   DB_USER       — DB 사용자                (기본: sportsify)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_CONTAINER="${DB_CONTAINER:-sportsify-postgres}"
DB_NAME="${DB_NAME:-sportsify}"
DB_USER="${DB_USER:-sportsify}"

psql_exec() {
    docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

TARGET="${1:-all}"
NO_CLEANUP=false
for arg in "$@"; do
    [[ "$arg" == "--noCleanUp" ]] && NO_CLEANUP=true
done

cleanup() {
    if [[ "$NO_CLEANUP" == true ]]; then
        echo ""
        echo "⚠ [cleanup 생략] seed 데이터가 DB에 남아 있습니다. 수동 정리: psql ... -f k6/chat/seed_cleanup.sql"
        return
    fi
    echo ""
    echo "▶ [cleanup] seed_cleanup.sql 실행 중..."
    psql_exec < "$SCRIPT_DIR/seed_cleanup.sql" && echo "✔ cleanup 완료" || echo "✘ cleanup 실패 (수동 확인 필요)"
}
trap cleanup EXIT

echo "▶ [seed] seed.sql 실행 중..."
psql_exec < "$SCRIPT_DIR/seed.sql"
echo "✔ seed 완료"
echo ""

run_k6() {
    local script="$1"
    echo "▶ [k6] $script 실행 중..."
    k6 run -e BASE_URL="$BASE_URL" "$SCRIPT_DIR/$script"
    echo "✔ $script 완료"
    echo ""
}

case "$TARGET" in
    ramping)
        run_k6 ramping.js
        ;;
    constant)
        run_k6 constant.js
        ;;
    all)
        run_k6 ramping.js
        run_k6 constant.js
        ;;
    *)
        echo "사용법: $0 [ramping|constant|all]"
        exit 1
        ;;
esac
