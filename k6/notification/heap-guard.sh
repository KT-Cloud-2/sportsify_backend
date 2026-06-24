#!/usr/bin/env bash
# k6/notification/heap-guard.sh
# sustain 부하테스트 중 서버 JVM 힙 사용률을 감시하다가 임계치 초과 시 서버를 강제 종료한다.
#
# 사용법:
#   ./k6/notification/heap-guard.sh &                  # 백그라운드로 감시 시작 (기본 임계치 90%)
#   THRESHOLD=80 ./k6/notification/heap-guard.sh &      # 임계치 환경변수로 지정
#   ./k6/notification/heap-guard.sh 85 &               # 임계치 인자로 지정
#   PID=12345 ./k6/notification/heap-guard.sh &         # 대상 PID 직접 지정 (기본: SportsifyApplication 자동 탐색)
#   K6_PID=23456 ./k6/notification/heap-guard.sh &      # 서버 종료 시 같이 죽일 k6 프로세스도 지정 (run.sh 용)
#
# run.sh 가 이 스크립트의 시작/종료를 자동으로 관리하므로 직접 실행할 필요는 없다.
# 종료: kill %1  (또는 jobs 로 PID 확인 후 kill)

set -uo pipefail

# 인자 > 환경변수 > 기본값(75) 순으로 적용
THRESHOLD="${1:-${THRESHOLD:-75}}"
INTERVAL="${INTERVAL:-5}"
CONTAINER="${CONTAINER:-sportsify-app}"
K6_PID="${K6_PID:-}"
MAX_READ_FAILURES="${MAX_READ_FAILURES:-5}"

_read_fail_count=0

# 컨테이너 존재 확인
if ! docker inspect "$CONTAINER" &>/dev/null; then
    echo "[heap-guard] 컨테이너 '${CONTAINER}'를 찾을 수 없습니다." >&2
    exit 1
fi

echo "[heap-guard] container=${CONTAINER} 감시 시작 (임계치 ${THRESHOLD}%, 주기 ${INTERVAL}s)"

APP_URL="${APP_URL:-http://localhost:8080}"

while true; do
    # 컨테이너가 살아있는지 확인
    STATUS="$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)"
    if [[ "$STATUS" != "true" ]]; then
        echo "[heap-guard] 컨테이너가 종료되었습니다. 감시를 중단합니다."
        exit 0
    fi

    # actuator metrics API로 JVM 힙 메트릭 읽기 (prometheus 전체보다 빠름)
    # --max-time 30: 고VU 부하 중 actuator 응답 지연 허용
    _used_raw="$(curl -sf --max-time 30 "${APP_URL}/actuator/metrics/jvm.memory.used?tag=area:heap" 2>/dev/null)"
    _curl_used_rc=$?
    USED_BYTES="$(echo "$_used_raw" | python3 -c "import sys,json; d=json.load(sys.stdin); print(int(sum(m['value'] for m in d['measurements'])))" 2>/dev/null)"

    _max_raw="$(curl -sf --max-time 30 "${APP_URL}/actuator/metrics/jvm.memory.max?tag=area:heap" 2>/dev/null)"
    _curl_max_rc=$?
    MAX_BYTES="$(echo "$_max_raw" | python3 -c "import sys,json; d=json.load(sys.stdin); print(int(sum(m['value'] for m in d['measurements'])))" 2>/dev/null)"

    if [[ -z "$USED_BYTES" || -z "$MAX_BYTES" || "$MAX_BYTES" -le 0 ]]; then
        (( _read_fail_count++ )) || true
        # curl exit code 28=timeout, 7=connection refused
        echo "[heap-guard] heap 정보를 읽지 못했습니다. ${INTERVAL}s 후 재시도. (${_read_fail_count}/${MAX_READ_FAILURES}) [curl_rc: used=${_curl_used_rc} max=${_curl_max_rc}]" >&2
        if (( _read_fail_count >= MAX_READ_FAILURES )); then
            echo "[heap-guard] 연속 ${MAX_READ_FAILURES}회 읽기 실패 — 부하테스트를 종료합니다." >&2
            if [[ -n "$K6_PID" ]] && kill -0 "$K6_PID" 2>/dev/null; then
                kill -15 "$K6_PID"
            fi
            exit 1
        fi
        sleep "$INTERVAL"
        continue
    fi

    _read_fail_count=0

    USED_M=$(( USED_BYTES / 1024 / 1024 ))
    MAX_M=$(( MAX_BYTES / 1024 / 1024 ))
    USAGE_PCT=$(( USED_M * 100 / MAX_M ))
    echo "[heap-guard] heap used=${USED_M}M / max=${MAX_M}M (${USAGE_PCT}%)"

    if (( USAGE_PCT >= THRESHOLD )); then
        echo "[heap-guard] 임계치(${THRESHOLD}%) 초과 감지 (${USAGE_PCT}%). 컨테이너 재시작."
        docker restart "$CONTAINER"

        if [[ -n "$K6_PID" ]] && kill -0 "$K6_PID" 2>/dev/null; then
            echo "[heap-guard] k6 PID=${K6_PID} 도 함께 종료."
            kill -15 "$K6_PID"
        fi

        echo "[heap-guard] 종료 완료."
        exit 0
    fi

    sleep "$INTERVAL"
done
