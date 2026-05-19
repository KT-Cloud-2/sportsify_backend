-- 시나리오 테스트 참조 데이터 — ScenarioTestSupport @Sql로 로드
-- 매 테스트 메서드 전에 실행, ON CONFLICT DO NOTHING으로 멱등 보장
-- game_id=1 (ON_SALE), seat_id=2 (시나리오 1), seat_id=3 (시나리오 3) 필수

INSERT INTO teams (id, name, short_name, sport_type, logo_url, is_active, created_at)
VALUES (1,  '두산 베어스', '두산', 'BASEBALL', NULL, TRUE, NOW()),
       (2,  'LG 트윈스',   'LG',   'BASEBALL', NULL, TRUE, NOW())
ON CONFLICT DO NOTHING;

INSERT INTO stadiums (id, name, address, total_seats)
VALUES (1, '잠실 야구장', '서울 송파구 올림픽로 19-2', 25000)
ON CONFLICT DO NOTHING;

INSERT INTO zone_grades (id, stadium_id, name)
VALUES (1, 1, 'VIP')
ON CONFLICT DO NOTHING;

INSERT INTO sections (id, stadium_id, zone_grade_id, name, floor)
VALUES (1, 1, 1, 'VIP-A', '1F')
ON CONFLICT DO NOTHING;

INSERT INTO seats (id, section_id, zone_grade_id, row_number, seat_number)
VALUES (1,  1, 1, 'A', '1'),
       (2,  1, 1, 'A', '2'),
       (3,  1, 1, 'A', '3')
ON CONFLICT DO NOTHING;

INSERT INTO price_policies (stadium_id, day_type, zone_grade_id, game_grade, price)
VALUES (1, 'WEEKEND', 1, 'NORMAL', 50000)
ON CONFLICT DO NOTHING;

INSERT INTO games (id, stadium_id, home_team_id, away_team_id, sport_type, start_at, status,
                   day_type, game_grade, max_ticket_per_user, sale_start_at, sale_end_at, created_at)
VALUES (1, 1, 1, 2, 'BASEBALL', NOW() + INTERVAL '3 days', 'ON_SALE',
        'WEEKEND', 'NORMAL', 4, NOW() - INTERVAL '2 days', NOW() + INTERVAL '2 days', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO game_seats (game_id, seat_id, seat_status, price)
VALUES (1, 1, 'RESERVED',  50000),
       (1, 2, 'AVAILABLE', 50000),
       (1, 3, 'AVAILABLE', 50000)
ON CONFLICT DO NOTHING;

-- 각 테스트 전 시나리오 좌석 상태 초기화 (이전 테스트의 RESERVED/SOLD 상태 복구)
UPDATE game_seats SET seat_status = 'AVAILABLE' WHERE game_id = 1 AND seat_id IN (2, 3);
UPDATE game_seats SET seat_status = 'RESERVED'  WHERE game_id = 1 AND seat_id = 1;
