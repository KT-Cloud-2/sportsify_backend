import http from 'k6/http';
import {check, group} from 'k6';

const VUS = parseInt(__ENV.VUS) || 100;

export const options = {
    scenarios: {
        success_flow: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.95'],

        'http_req_duration{step:reservation}': ['p(95)<2000'],
        'http_req_duration{step:payment}': ['p(95)<5000'],
        'http_req_duration{step:payment_confirm}': ['p(95)<3000'],
        'http_req_duration{step:ticket_check}': ['p(95)<2000'],

        'http_req_failed{step:payment}': ['rate<0.005'],

        // 처리량
        http_reqs: ['rate>10'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    const tokens = [];
    for (let i = 1; i <= VUS; i++) {
        const res = http.get(`${BASE_URL}/dev/token?memberId=${i}&role=USER`);
        tokens.push(JSON.parse(res.body).token);
    }

    warmUp(tokens);

    return {tokens};
}

function warmUp(tokens) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${tokens[0]}`,
    };

    console.log("🔥 웜업 시작: 단순 조회 및 주요 로직 호출");
    for (let i = 0; i < VUS; i++) {
        http.get(`${BASE_URL}/api/tickets?page=0&size=10`, {headers});
    }
    console.log("✅ 웜업 종료");
}

export default function (data) {
    const vuId = __VU;
    const token = data.tokens[vuId - 1];
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };

    const seatId = vuId;

    // ========== 1. 좌석 예약 ==========
    let orderId, amount;

    group('1. 좌석 예약', () => {
        const body = JSON.stringify({
            gameId: 1,
            seatIds: [seatId],
        });

        const res = http.post(`${BASE_URL}/api/seats/reservations`,
                body,
                {headers, tags: {step: 'reservation'}}
        );

        const success = check(res, {
            '예약 성공 (200)': (r) => r.status === 200 && JSON.parse(r.body).orderId !== undefined,
        });

        if (!success) {
            console.log(`VU ${vuId} | ❌ 1.예약 실패 | status: ${res.status} | body: ${res.body}`);
        } else {
            const resBody = JSON.parse(res.body);
            orderId = resBody.orderId;
            amount = resBody.amount;
        }
    });

    if (!orderId) {
        return;
    }

    // ========== 2. 결제 생성 ==========
    let tossOrderId;

    group('2. 결제 생성', () => {
        const body = JSON.stringify({
            orderId: orderId,
            matchId: 1,
            seatId: seatId,
            amount: amount,
            paymentMethod: 'CARD',
            idempotencyKey: `idem-${vuId}-${Date.now()}`,
        });

        const res = http.post(`${BASE_URL}/api/payments`,
                body,
                {headers, tags: {step: 'payment'}}
        );

        const success = check(res, {
            '결제 생성 성공 (200)': (r) => r.status === 200 && JSON.parse(r.body).paymentId !== undefined,
        });

        if (!success) {
            console.log(`VU ${vuId} | ❌ 2.결제생성 실패 | status: ${res.status} | body: ${res.body}`);
        } else {
            const resBody = JSON.parse(res.body);
            tossOrderId = resBody.tossOrderId;
        }
    });

    if (!tossOrderId) {
        return;
    }

    // ========== 3. 결제 확인 ==========
    let confirmSuccess = false;

    group('3. 결제 확인', () => {
        const mockPaymentKey = `toss_pk_${vuId}_${Date.now()}`;

        const body = JSON.stringify({
            paymentKey: mockPaymentKey,
            tossOrderId: tossOrderId,
            amount: amount,
        });

        const res = http.post(`${BASE_URL}/api/payments/confirm`,
                body,
                {headers, tags: {step: 'payment_confirm'}}
        );

        const success = check(res, {
            '결제 확인 성공 (200)': (r) => r.status === 200 && JSON.parse(r.body).status === 'COMPLETED',
        });

        if (!success) {
            console.log(`VU ${vuId} | ❌ 3.결제확인 실패 | status: ${res.status} | body: ${res.body}`);
        } else {
            confirmSuccess = true;
        }
    });

    if (!confirmSuccess) {
        return;
    }

    let ticketFound = false;
    // ========== 4. 티켓 확인 - Facade ==========
    group('4. 티켓 확인', () => {
        const res = http.get(`${BASE_URL}/api/tickets?page=0&size=10`,
                {headers, tags: {step: 'ticket_check'}});

        ticketFound = check(res, {
            '티켓 조회 성공 (200)': (r) => r.status === 200 && JSON.parse(r.body)?.totalCount > 0,
        });
        if (!ticketFound) {
            console.log(`VU ${vuId} | ❌ 4.티켓조회 실패 | status: ${res.status} | body: ${res.body})`);
        }
    });
}
