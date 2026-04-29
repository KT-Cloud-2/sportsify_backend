-- ============================================================
-- V2 — 더미 데이터 (예약 기능 테스트용)
-- ============================================================

-- Members (회원)
INSERT INTO members (email, nickname, provider, provider_id, status, role, created_at, last_login_at)
VALUES ('test1@example.com', '야구팬1', 'GOOGLE', 'google_123', 'ACTIVE', 'USER', NOW(), NOW()),
       ('test2@example.com', '축구팬2', 'KAKAO', 'kakao_456', 'ACTIVE', 'USER', NOW(), NOW()),
       ('test3@example.com', '농구팬3', 'GOOGLE', 'google_789', 'ACTIVE', 'USER', NOW(), NOW()),
       ('admin@example.com', '관리자', 'GOOGLE', 'google_admin', 'ACTIVE', 'ADMIN', NOW(), NOW()),
       ('test4@example.com', '스포츠팬4', 'KAKAO', 'kakao_101', 'ACTIVE', 'USER', NOW(), NOW());

-- Teams (구단)
INSERT INTO teams (name, short_name, sport_type, logo_url, is_active, created_at)
VALUES
    -- 야구팀
    ('LG 트윈스', 'LG', 'BASEBALL', 'https://example.com/lg_logo.png', TRUE, NOW()),
    ('두산 베어스', '두산', 'BASEBALL', 'https://example.com/doosan_logo.png', TRUE, NOW()),
    ('키움 히어로즈', '키움', 'BASEBALL', 'https://example.com/kiwoom_logo.png', TRUE, NOW()),
    ('삼성 라이온즈', '삼성', 'BASEBALL', 'https://example.com/samsung_logo.png', TRUE, NOW()),
    -- 축구팀
    ('FC 서울', '서울', 'FOOTBALL', 'https://example.com/seoul_logo.png', TRUE, NOW()),
    ('수원 삼성', '수원', 'FOOTBALL', 'https://example.com/suwon_logo.png', TRUE, NOW()),
    ('울산 현대', '울산', 'FOOTBALL', 'https://example.com/ulsan_logo.png', TRUE, NOW()),
    -- 농구팀
    ('서울 SK 나이츠', 'SK', 'BASKETBALL', 'https://example.com/sk_logo.png', TRUE, NOW()),
    ('원주 DB', 'DB', 'BASKETBALL', 'https://example.com/db_logo.png', TRUE, NOW());

-- Stadiums (경기장)
INSERT INTO stadiums (name, address, total_seats)
VALUES ('잠실야구장', '서울특별시 송파구 올림픽로 25', 80000),
       ('고척스카이돔', '서울특별시 구로구 경인로 430', 90000),
       ('상암월드컵경기장', '서울특별시 마포구 월드컵로 240', 150000),
       ('잠실실내체육관', '서울특별시 송파구 올림픽로 25', 100000);

-- Zone Grades (구역 등급)
-- 잠실야구장 (stadium_id: 1)
INSERT INTO zone_grades (stadium_id, name)
VALUES (1, 'VIP'),
       (1, 'MVP'),
       (1, 'ROYAL'),
       (1, 'ORANGE'),
       (1, 'RED'),
       (1, 'NAVY'),
       (1, 'GREEN'),
       (1, 'SKY'),
       (1, 'EXCITING');

-- 고척스카이돔 (stadium_id: 2)
INSERT INTO zone_grades (stadium_id, name)
VALUES (2, 'PREMIUM'),
       (2, 'DIAMOND'),
       (2, 'PLATINUM'),
       (2, 'GOLD'),
       (2, 'SILVER');

-- 상암월드컵경기장 (stadium_id: 3)
INSERT INTO zone_grades (stadium_id, name)
VALUES (3, 'VIP'),
       (3, 'PREMIUM'),
       (3, 'GENERAL');

-- 잠실실내체육관 (stadium_id: 4)
INSERT INTO zone_grades (stadium_id, name)
VALUES (4, 'COURTSIDE'),
       (4, 'PREMIUM'),
       (4, 'GENERAL');

-- Sections (구역)
-- 잠실야구장
INSERT INTO sections (stadium_id, zone_grade_id, name, floor)
VALUES (1, 1, 'VIP-A', '2F'),
       (1, 1, 'VIP-B', '2F'),
       (1, 2, 'MVP-101', '1F'),
       (1, 2, 'MVP-102', '1F'),
       (1, 3, 'ROYAL-201', '2F'),
       (1, 4, 'ORANGE-301', '3F'),
       (1, 5, 'RED-302', '3F'),
       (1, 6, 'NAVY-401', '4F'),
       (1, 7, 'GREEN-402', '4F');

-- 고척스카이돔
INSERT INTO sections (stadium_id, zone_grade_id, name, floor)
VALUES (2, 10, 'PREMIUM-A', '2F'),
       (2, 11, 'DIAMOND-101', '1F'),
       (2, 12, 'PLATINUM-201', '2F');

-- Seats (좌석)
-- VIP-A 구역 (section_id: 1, zone_grade_id: 1)
INSERT INTO seats (section_id, zone_grade_id, row_number, seat_number)
VALUES (1, 1, 'A', '1'),
       (1, 1, 'A', '2'),
       (1, 1, 'A', '3'),
       (1, 1, 'A', '4'),
       (1, 1, 'B', '1'),
       (1, 1, 'B', '2'),
       (1, 1, 'B', '3'),
       (1, 1, 'B', '4'),
       (1, 1, 'C', '1'),
       (1, 1, 'C', '2'),
       (1, 1, 'C', '3'),
       (1, 1, 'C', '4');

-- VIP-B 구역 (section_id: 2, zone_grade_id: 1)
INSERT INTO seats (section_id, zone_grade_id, row_number, seat_number)
VALUES (2, 1, 'A', '1'),
       (2, 1, 'A', '2'),
       (2, 1, 'A', '3'),
       (2, 1, 'A', '4'),
       (2, 1, 'B', '1'),
       (2, 1, 'B', '2'),
       (2, 1, 'B', '3'),
       (2, 1, 'B', '4');

-- MVP-101 구역 (section_id: 3, zone_grade_id: 2)
INSERT INTO seats (section_id, zone_grade_id, row_number, seat_number)
VALUES (3, 2, '1', '1'),
       (3, 2, '1', '2'),
       (3, 2, '1', '3'),
       (3, 2, '1', '4'),
       (3, 2, '1', '5'),
       (3, 2, '2', '1'),
       (3, 2, '2', '2'),
       (3, 2, '2', '3'),
       (3, 2, '2', '4'),
       (3, 2, '2', '5'),
       (3, 2, '3', '1'),
       (3, 2, '3', '2'),
       (3, 2, '3', '3'),
       (3, 2, '3', '4'),
       (3, 2, '3', '5');

-- ROYAL-201 구역 (section_id: 5, zone_grade_id: 3)
INSERT INTO seats (section_id, zone_grade_id, row_number, seat_number)
VALUES (5, 3, '10', '1'),
       (5, 3, '10', '2'),
       (5, 3, '10', '3'),
       (5, 3, '10', '4'),
       (5, 3, '11', '1'),
       (5, 3, '11', '2'),
       (5, 3, '11', '3'),
       (5, 3, '11', '4'),
       (5, 3, '12', '1'),
       (5, 3, '12', '2'),
       (5, 3, '12', '3'),
       (5, 3, '12', '4');

-- Games (경기)
INSERT INTO games (stadium_id, home_team_id, away_team_id, sport_type, start_at, duration_minutes, status, day_type,
                   game_grade, max_ticket_per_user, sale_start_at, sale_end_at, created_at)
VALUES
    -- 예매 중인 야구 경기 (1주일 후)
    (1, 1, 2, 'BASEBALL', NOW() + INTERVAL '7 days', 180, 'ON_SALE', 'WEEKEND', 'RIVAL', 4,
     NOW() - INTERVAL '1 day', NOW() + INTERVAL '6 days', NOW()),

    -- 예매 예정인 야구 경기 (2주일 후)
    (1, 3, 4, 'BASEBALL', NOW() + INTERVAL '14 days', 180, 'SCHEDULED', 'WEEKDAY', 'NORMAL', 4,
     NOW() + INTERVAL '3 days', NOW() + INTERVAL '13 days', NOW()),

    -- 거의 매진된 경기 (Lock 테스트용)
    (1, 1, 3, 'BASEBALL', NOW() + INTERVAL '10 days', 180, 'ON_SALE', 'WEEKEND', 'RIVAL', 2,
     NOW() - INTERVAL '2 days', NOW() + INTERVAL '9 days', NOW()),

    -- 축구 경기
    (1, 5, 6, 'FOOTBALL', NOW() + INTERVAL '5 days', 120, 'ON_SALE', 'WEEKEND', 'NORMAL', 4,
     NOW() - INTERVAL '1 day', NOW() + INTERVAL '4 days', NOW()),

    -- 농구 경기
    (1, 8, 9, 'BASKETBALL', NOW() + INTERVAL '3 days', 150, 'ON_SALE', 'WEEKDAY', 'NORMAL', 4,
     NOW() - INTERVAL '1 day', NOW() + INTERVAL '2 days', NOW());

-- Game Seats (경기별 좌석)
-- 게임 1 (LG vs 두산, 잠실)
INSERT INTO game_seats (game_id, seat_id, price, seat_status)
VALUES (1, 1, 80000, 'AVAILABLE'),
       (1, 2, 80000, 'SOLD'),
       (1, 3, 80000, 'AVAILABLE'),
       (1, 4, 80000, 'RESERVED'),
       (1, 5, 80000, 'AVAILABLE'),
       (1, 6, 80000, 'AVAILABLE'),
       (1, 7, 80000, 'SOLD'),
       (1, 8, 80000, 'AVAILABLE'),
       (1, 21, 50000, 'AVAILABLE'),
       (1, 22, 50000, 'AVAILABLE'),
       (1, 23, 50000, 'SOLD'),
       (1, 24, 50000, 'AVAILABLE'),
       (1, 25, 50000, 'AVAILABLE'),
       (1, 36, 35000, 'AVAILABLE'),
       (1, 37, 35000, 'AVAILABLE'),
       (1, 38, 35000, 'RESERVED'),
       (1, 39, 35000, 'AVAILABLE');

-- 게임 3 (거의 매진된 경기, Lock 테스트용)
INSERT INTO game_seats (game_id, seat_id, price, seat_status)
VALUES (3, 1, 45000, 'SOLD'),
       (3, 2, 45000, 'SOLD'),
       (3, 3, 45000, 'AVAILABLE'),
       (3, 4, 45000, 'SOLD'),
       (3, 5, 45000, 'AVAILABLE'),
       (3, 6, 40000, 'AVAILABLE');

-- 게임 4 (축구 경기)
INSERT INTO game_seats (game_id, seat_id, price, seat_status)
VALUES (4, 1, 30000, 'AVAILABLE'),
       (4, 2, 30000, 'AVAILABLE'),
       (4, 3, 30000, 'AVAILABLE'),
       (4, 4, 25000, 'AVAILABLE');

-- 게임 5 (농구 경기)
INSERT INTO game_seats (game_id, seat_id, price, seat_status)
VALUES (5, 1, 60000, 'AVAILABLE'),
       (5, 2, 60000, 'SOLD'),
       (5, 3, 60000, 'AVAILABLE'),
       (5, 4, 50000, 'AVAILABLE');

-- Price Policies (가격 정책)
-- 잠실야구장 (stadium_id: 1)
INSERT INTO price_policies (stadium_id, day_type, zone_grade_id, game_grade, price)
VALUES
    -- VIP 구역 (zone_grade_id: 1)
    (1, 'WEEKDAY', 1, 'REGULAR', 70000),
    (1, 'WEEKEND', 1, 'REGULAR', 80000),
    (1, 'HOLIDAY', 1, 'REGULAR', 85000),
    (1, 'WEEKDAY', 1, 'PLAYOFF', 90000),
    (1, 'WEEKEND', 1, 'PLAYOFF', 100000),
    (1, 'HOLIDAY', 1, 'PLAYOFF', 110000),
    (1, 'WEEKDAY', 1, 'FINAL', 120000),
    (1, 'WEEKEND', 1, 'FINAL', 130000),
    (1, 'HOLIDAY', 1, 'FINAL', 140000),
    -- MVP 구역 (zone_grade_id: 2)
    (1, 'WEEKDAY', 2, 'REGULAR', 40000),
    (1, 'WEEKEND', 2, 'REGULAR', 50000),
    (1, 'HOLIDAY', 2, 'REGULAR', 55000),
    (1, 'WEEKDAY', 2, 'PLAYOFF', 60000),
    (1, 'WEEKEND', 2, 'PLAYOFF', 70000),
    (1, 'HOLIDAY', 2, 'PLAYOFF', 75000),
    (1, 'WEEKDAY', 2, 'FINAL', 80000),
    (1, 'WEEKEND', 2, 'FINAL', 90000),
    (1, 'HOLIDAY', 2, 'FINAL', 95000),
    -- ROYAL 구역 (zone_grade_id: 3)
    (1, 'WEEKDAY', 3, 'REGULAR', 30000),
    (1, 'WEEKEND', 3, 'REGULAR', 35000),
    (1, 'HOLIDAY', 3, 'REGULAR', 38000),
    (1, 'WEEKDAY', 3, 'PLAYOFF', 45000),
    (1, 'WEEKEND', 3, 'PLAYOFF', 50000),
    (1, 'HOLIDAY', 3, 'PLAYOFF', 53000),
    (1, 'WEEKDAY', 3, 'FINAL', 60000),
    (1, 'WEEKEND', 3, 'FINAL', 65000),
    (1, 'HOLIDAY', 3, 'FINAL', 68000),
    -- ORANGE 구역 (zone_grade_id: 4)
    (1, 'WEEKDAY', 4, 'REGULAR', 25000),
    (1, 'WEEKEND', 4, 'REGULAR', 28000),
    (1, 'HOLIDAY', 4, 'REGULAR', 30000),
    (1, 'WEEKDAY', 4, 'PLAYOFF', 35000),
    (1, 'WEEKEND', 4, 'PLAYOFF', 40000),
    (1, 'HOLIDAY', 4, 'PLAYOFF', 42000),
    -- RED 구역 (zone_grade_id: 5)
    (1, 'WEEKDAY', 5, 'REGULAR', 20000),
    (1, 'WEEKEND', 5, 'REGULAR', 23000),
    (1, 'HOLIDAY', 5, 'REGULAR', 25000),
    (1, 'WEEKDAY', 5, 'PLAYOFF', 30000),
    (1, 'WEEKEND', 5, 'PLAYOFF', 33000),
    (1, 'HOLIDAY', 5, 'PLAYOFF', 35000);

-- 고척스카이돔 (stadium_id: 2)
INSERT INTO price_policies (stadium_id, day_type, zone_grade_id, game_grade, price)
VALUES
    -- PREMIUM 구역 (zone_grade_id: 10)
    (2, 'WEEKDAY', 10, 'REGULAR', 60000),
    (2, 'WEEKEND', 10, 'REGULAR', 70000),
    (2, 'HOLIDAY', 10, 'REGULAR', 75000),
    (2, 'WEEKDAY', 10, 'PLAYOFF', 80000),
    (2, 'WEEKEND', 10, 'PLAYOFF', 90000),
    (2, 'HOLIDAY', 10, 'PLAYOFF', 95000),
    -- DIAMOND 구역 (zone_grade_id: 11)
    (2, 'WEEKDAY', 11, 'REGULAR', 35000),
    (2, 'WEEKEND', 11, 'REGULAR', 40000),
    (2, 'HOLIDAY', 11, 'REGULAR', 43000),
    (2, 'WEEKDAY', 11, 'PLAYOFF', 50000),
    (2, 'WEEKEND', 11, 'PLAYOFF', 55000),
    (2, 'HOLIDAY', 11, 'PLAYOFF', 58000),
    -- PLATINUM 구역 (zone_grade_id: 12)
    (2, 'WEEKDAY', 12, 'REGULAR', 28000),
    (2, 'WEEKEND', 12, 'REGULAR', 32000),
    (2, 'HOLIDAY', 12, 'REGULAR', 35000),
    (2, 'WEEKDAY', 12, 'PLAYOFF', 40000),
    (2, 'WEEKEND', 12, 'PLAYOFF', 45000),
    (2, 'HOLIDAY', 12, 'PLAYOFF', 48000);

-- 상암월드컵경기장 (stadium_id: 3)
INSERT INTO price_policies (stadium_id, day_type, zone_grade_id, game_grade, price)
VALUES
    -- VIP 구역 (zone_grade_id: 15)
    (3, 'WEEKDAY', 15, 'REGULAR', 80000),
    (3, 'WEEKEND', 15, 'REGULAR', 90000),
    (3, 'HOLIDAY', 15, 'REGULAR', 95000),
    (3, 'WEEKDAY', 15, 'PLAYOFF', 120000),
    (3, 'WEEKEND', 15, 'PLAYOFF', 130000),
    -- PREMIUM 구역 (zone_grade_id: 16)
    (3, 'WEEKDAY', 16, 'REGULAR', 50000),
    (3, 'WEEKEND', 16, 'REGULAR', 55000),
    (3, 'HOLIDAY', 16, 'REGULAR', 60000),
    (3, 'WEEKDAY', 16, 'PLAYOFF', 70000),
    (3, 'WEEKEND', 16, 'PLAYOFF', 75000),
    -- GENERAL 구역 (zone_grade_id: 17)
    (3, 'WEEKDAY', 17, 'REGULAR', 25000),
    (3, 'WEEKEND', 17, 'REGULAR', 30000),
    (3, 'HOLIDAY', 17, 'REGULAR', 32000),
    (3, 'WEEKDAY', 17, 'PLAYOFF', 40000),
    (3, 'WEEKEND', 17, 'PLAYOFF', 45000);

-- 잠실실내체육관 (stadium_id: 4)
INSERT INTO price_policies (stadium_id, day_type, zone_grade_id, game_grade, price)
VALUES
    -- COURTSIDE 구역 (zone_grade_id: 18)
    (4, 'WEEKDAY', 18, 'REGULAR', 100000),
    (4, 'WEEKEND', 18, 'REGULAR', 110000),
    (4, 'HOLIDAY', 18, 'REGULAR', 120000),
    (4, 'WEEKDAY', 18, 'PLAYOFF', 150000),
    (4, 'WEEKEND', 18, 'PLAYOFF', 160000),
    -- PREMIUM 구역 (zone_grade_id: 19)
    (4, 'WEEKDAY', 19, 'REGULAR', 60000),
    (4, 'WEEKEND', 19, 'REGULAR', 65000),
    (4, 'HOLIDAY', 19, 'REGULAR', 70000),
    (4, 'WEEKDAY', 19, 'PLAYOFF', 80000),
    (4, 'WEEKEND', 19, 'PLAYOFF', 85000),
    -- GENERAL 구역 (zone_grade_id: 20)
    (4, 'WEEKDAY', 20, 'REGULAR', 40000),
    (4, 'WEEKEND', 20, 'REGULAR', 45000),
    (4, 'HOLIDAY', 20, 'REGULAR', 48000),
    (4, 'WEEKDAY', 20, 'PLAYOFF', 55000),
    (4, 'WEEKEND', 20, 'PLAYOFF', 60000);
