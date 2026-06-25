/**
 * [C] 브로드캐스트 전파 검증 테스트 — Ramping 패턴
 *
 * 시나리오: sender 1명이 메시지 전송 → 같은 방의 receiver들이 수신하는지 검증
 * 목적:     브로드캐스트 전파 완전성(누락 없음) + sender→receiver 전파 레이턴시 측정
 *
 * VU 구성 (MAX_VUS 기준):
 *   VU  1~5       : sender  — 각 방에 1명, SENDER_START_DELAY_S 후 메시지 전송
 *   VU  6~MAX_VUS : receiver — 5개 방에 라운드로빈 배정, 즉시 구독 후 수신 대기
 *
 * 방/멤버 범위:
 *   rooms    : 9041~9045 (5개 고정)
 *   senders  : member 40000~40004  (VU 1~5)
 *   receivers: member 40005~40000+MAX_VUS-1 (VU 6~MAX_VUS)
 *
 * 실행:
 *   ./k6/chat/run.sh broadcast 1000   → 600→700→800→900→1000 VU 단계적 증가
 *   ./k6/chat/run.sh broadcast         → 기본 500 VU (100→200→300→400→500)
 *
 * 사전 조건:
 *   - k6/chat/seed.sql 실행 (broadcast 섹션, 최대 1000 VU 지원)
 *   - /dev/token?memberId={id} 엔드포인트 활성화 (local 프로파일)
 */

import ws from 'k6/ws';
import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';
import {stomp} from './lib/stomp.js';

const deliveryTime = new Trend('broadcast_delivery_ms', true);
const broadcastReceived = new Counter('broadcast_received');
const broadcastMissed = new Counter('broadcast_missed');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/chat';

const MAX_VUS = parseInt(__ENV.MAX_VUS || '500');
const ROOM_COUNT = 5;
const MESSAGES_PER_SENDER = 10;
const SENDER_START_DELAY_S = 8;   // receivers 구독 완료 대기

// VU idx 0~4: sender,   room = 9041 + idx
// VU idx 5~MAX_VUS-1: receiver, room = 9041 + (receiverIdx % ROOM_COUNT)
const ROOM_IDS = Array.from({length: ROOM_COUNT}, (_, i) => 9041 + i);
const MEMBER_OFFSET = 40000;

// receiver 타임아웃: sender 대기(8s) + 연결·구독(~2s) + 전파 버퍼(8s)
const RECEIVER_TIMEOUT_MS = (SENDER_START_DELAY_S + 10) * 1000;

export const options = {
    scenarios: {
        broadcast: {
            executor: 'per-vu-iterations',
            vus: MAX_VUS,
            iterations: 1,
            maxDuration: '5m',
        },
    },
    thresholds: {
        broadcast_delivery_ms: ['p(95)<2000'],
        broadcast_missed: ['count==0'],
        broadcast_received: ['count>0'],
    },
};

export function setup() {
    const tokens = [];
    for (let i = 0; i < MAX_VUS; i++) {
        const res = http.get(`${BASE_URL}/dev/token?memberId=${MEMBER_OFFSET + i}`);
        check(res, {'[setup] token issued': (r) => r.status === 200});
        tokens.push(res.status === 200 ? JSON.parse(res.body).token : null);
        if (i % 10 === 9) sleep(0.1);
    }
    return {tokens};
}

export default function (data) {
    const vuIdx = __VU - 1;
    if (vuIdx < ROOM_COUNT) {
        runSender(data, vuIdx);
    } else {
        runReceiver(data, vuIdx - ROOM_COUNT);
    }
}

function runSender(data, senderIdx) {
    sleep(SENDER_START_DELAY_S);

    const token = data.tokens[senderIdx];
    const roomId = ROOM_IDS[senderIdx];
    const state = {subscribed: false};

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(stomp.connect(token)));

        socket.on('message', (raw) => {
            const cmd = stomp.command(raw);
            if (cmd === 'CONNECTED') {
                socket.send(stomp.subscribe('sub-0', `/topic/rooms/${roomId}`));
            } else if (cmd === 'MESSAGE') {
                const body = stomp.parseBody(raw);
                if (!state.subscribed && body?.type === 'SUBSCRIBED') {
                    state.subscribed = true;
                    for (let seq = 0; seq < MESSAGES_PER_SENDER; seq++) {
                        socket.send(stomp.send('/app/chat.send', {
                            clientMessageId: `k6-bc-${roomId}-${seq}-${Date.now()}`,
                            roomId,
                            type: 'TEXT',
                            content: JSON.stringify({_ts: Date.now(), _seq: seq}),
                        }));
                    }
                    socket.setTimeout(() => socket.close(), 3000);
                }
            } else if (cmd === 'ERROR') {
                socket.close();
            }
        });

        socket.on('error', () => socket.close());
        socket.setTimeout(() => socket.close(), 60_000);
    });

    check(res, {'[sender] ws handshake 101': (r) => r && r.status === 101});
}

function runReceiver(data, receiverIdx) {
    const token = data.tokens[ROOM_COUNT + receiverIdx];
    const roomId = ROOM_IDS[receiverIdx % ROOM_COUNT];

    const state = {
        subscribed: false,
        receivedSeqs: new Set(),
    };

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(stomp.connect(token)));

        socket.on('message', (raw) => {
            const cmd = stomp.command(raw);
            if (cmd === 'CONNECTED') {
                socket.send(stomp.subscribe('sub-0', `/topic/rooms/${roomId}`));
            } else if (cmd === 'MESSAGE') {
                const body = stomp.parseBody(raw);
                if (!state.subscribed) {
                    if (body?.type === 'SUBSCRIBE_FAILED') {
                        socket.close();
                        return;
                    }
                    if (body?.type === 'SUBSCRIBED') {
                        state.subscribed = true;
                    }
                    return;
                }
                if (body?.event === 'MESSAGE_SENT') {
                    try {
                        const inner = JSON.parse(body.payload?.content);
                        if (inner?._ts !== undefined && inner?._seq !== undefined) {
                            deliveryTime.add(Date.now() - inner._ts);
                            broadcastReceived.add(1);
                            state.receivedSeqs.add(inner._seq);
                        }
                    } catch (_) {
                    }
                }
            } else if (cmd === 'ERROR') {
                socket.close();
            }
        });

        socket.on('error', () => socket.close());

        socket.setTimeout(() => {
            if (state.subscribed) {
                const missedCount = MESSAGES_PER_SENDER - state.receivedSeqs.size;
                if (missedCount > 0) broadcastMissed.add(missedCount);
            }
            socket.close();
        }, RECEIVER_TIMEOUT_MS);
    });

    check(res, {'[receiver] ws handshake 101': (r) => r && r.status === 101});
}
