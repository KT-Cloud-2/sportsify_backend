import http from 'k6/http';

const SEED_COUNT = 10000;

export function getMemberId(vuId) {
    const seedOffset = parseInt(__ENV.K6_SEED_OFFSET || '10000', 10);
    return seedOffset + (vuId % SEED_COUNT);
}

/**
 * setup() 컨텍스트에서 호출 — /dev/token/bulk API로 토큰 발급
 * SharedArray 불가(init context 한정) → setup()에서 직접 HTTP 호출
 * @param {string} baseUrl
 * @param {number} seedOffset
 * @param {number} vus
 * @param {number} expiryMs  토큰 유효기간 (기본 90분)
 * @returns {string[]}
 */
export function fetchTokens(baseUrl, seedOffset, vus, expiryMs = 5400000) {
    const bulkSize = 1000;
    const tokens = [];

    for (let offset = 0; offset < vus; offset += bulkSize) {
        const startId = seedOffset + offset + 1;
        const count = Math.min(bulkSize, vus - offset);
        const url = `${baseUrl}/dev/token/bulk?startMemberId=${startId}&count=${count}&role=USER&expiryMs=${expiryMs}`;
        const res = http.get(url, { tags: { type: 'setup' } });
        if (res.status !== 200) {
            throw new Error(`토큰 발급 실패 startMemberId=${startId} status=${res.status}`);
        }
        tokens.push(...JSON.parse(res.body));
    }

    return tokens;
}

/** 하위 호환용 — 빈 배열 반환 (run.sh 경로는 setup()이 대체) */
export function getPreloadedTokens() {
    return [];
}

export function getTokenFromCache(tokens, vuId) {
    if (!tokens || tokens.length === 0) return null;
    return tokens[(vuId - 1) % tokens.length];
}
