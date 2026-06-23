-- 1. 유저 10000명
INSERT INTO members (id, email, nickname, provider, provider_id, status, role, created_at)
SELECT g,
       'user' || g || '@test.com',
       'tester' || g,
       'KAKAO',
       'kakao_' || g,
       'ACTIVE',
       'USER',
       NOW()
FROM generate_series(1, 10000) AS g
ON CONFLICT DO NOTHING;

-- 2. 팀
INSERT INTO teams (id, name, short_name, sport_type, logo_url, is_active, created_at)
VALUES (1, 'LG 트윈스', 'LG', 'BASEBALL', null, true, NOW()),
       (2, '두산 베어스', '두산', 'BASEBALL', null, true, NOW())
ON CONFLICT DO NOTHING;

-- 3. 스타디움
INSERT INTO stadiums (id, name, address, total_seats)
VALUES (1, '잠실야구장', '서울 송파구', 10000)
ON CONFLICT DO NOTHING;

-- 4. 등급
INSERT INTO zone_grades (id, stadium_id, name)
VALUES (1, 1, '프리미엄'),
       (2, 1, '1루'),
       (3, 1, '3루'),
       (4, 1, '외야')
ON CONFLICT DO NOTHING;

-- 5. 섹션
INSERT INTO sections (id, stadium_id, zone_grade_id, name, floor)
VALUES (1, 1, 1, 'A구역', '1F'),
       (2, 1, 2, 'B구역', '1F'),
       (3, 1, 3, 'C구역', '1F'),
       (4, 1, 4, 'D구역', '1F')
ON CONFLICT DO NOTHING;

-- 6. 좌석 (섹션당 2500개 = 총 10000개)
INSERT INTO seats (id, section_id, zone_grade_id, row_number, seat_number)
SELECT (s.section_idx - 1) * 2500 + seat_num,
       s.section_idx,
       s.zone_idx,
       'R' || ((seat_num - 1) / 50 + 1),
       LPAD(((seat_num - 1) % 50 + 1)::text, 2, '0')
FROM (VALUES (1, 1), (2, 2), (3, 3), (4, 4)) AS s(section_idx, zone_idx),
     generate_series(1, 2500) AS seat_num
ON CONFLICT DO NOTHING;

-- 7. 경기
INSERT INTO games (id, stadium_id, home_team_id, away_team_id, sport_type, start_at, duration_minutes, status, day_type, game_grade, max_ticket_per_user, sale_start_at, sale_end_at, created_at)
VALUES (1, 1, 1, 2, 'BASEBALL', NOW() + INTERVAL '7 days', 180, 'ON_SALE', 'WEEKDAY', 'NORMAL', 4, NOW() - INTERVAL '1 day', NOW() + INTERVAL '1 year', NOW())
ON CONFLICT DO NOTHING;

-- 8. 가격정책
INSERT INTO price_policies (id, stadium_id, day_type, zone_grade_id, game_grade, price)
VALUES (1, 1, 'WEEKDAY', 1, 'NORMAL', 80000),
       (2, 1, 'WEEKDAY', 2, 'NORMAL', 50000),
       (3, 1, 'WEEKDAY', 3, 'NORMAL', 50000),
       (4, 1, 'WEEKDAY', 4, 'NORMAL', 20000)
ON CONFLICT DO NOTHING;

-- 9. game_seats (경기별 좌석 10000개)
INSERT INTO game_seats (id, game_id, seat_id, seat_status, price)
SELECT seat_id,
       1,
       seat_id,
       'AVAILABLE',
       CASE
           WHEN seat_id <= 2500 THEN 80000
           WHEN seat_id <= 5000 THEN 50000
           WHEN seat_id <= 7500 THEN 50000
           ELSE 20000
           END
FROM generate_series(1, 10000) AS seat_id
ON CONFLICT DO NOTHING;
