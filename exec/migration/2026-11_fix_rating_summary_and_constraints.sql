-- =====================================================================
-- 운영 DB 수동 적용 스크립트 (2026-11, 리뷰·적재 동시성/무결성 수정에 따른 데이터 보정)
-- 애플리케이션이 자동 실행하지 않는다. 배포 전 백업 후 psql로 직접 실행한다.
-- 각 단계는 여러 번 실행해도 결과가 같다(멱등).
-- =====================================================================

BEGIN;

-- 1) 사용자 리뷰 평점 집계 재계산
--    회원 탈퇴 시 리뷰만 지우고 와인 집계를 빼지 않던 버그로, 리뷰가 없는데 user_review_count가 남은 와인이 있다.
--    실제 review 테이블 기준으로 사용자 집계(user_*)와 표시용 값(display_*, average_rating)을 다시 계산한다.
WITH r AS (
    SELECT wine_id,
           count(*)                          AS cnt,
           sum(rating)                       AS total,
           count(*) FILTER (WHERE rating = 1) AS s1,
           count(*) FILTER (WHERE rating = 2) AS s2,
           count(*) FILTER (WHERE rating = 3) AS s3,
           count(*) FILTER (WHERE rating = 4) AS s4,
           count(*) FILTER (WHERE rating = 5) AS s5
    FROM review GROUP BY wine_id
), calc AS (
    SELECT w.id,
           COALESCE(r.cnt, 0)   AS cnt,
           COALESCE(r.total, 0) AS total,
           COALESCE(r.s1, 0) AS s1, COALESCE(r.s2, 0) AS s2, COALESCE(r.s3, 0) AS s3,
           COALESCE(r.s4, 0) AS s4, COALESCE(r.s5, 0) AS s5,
           COALESCE(w.external_rating_count, 0) + COALESCE(r.cnt, 0) AS display_cnt,
           COALESCE(w.external_average_rating, 0) * COALESCE(w.external_rating_count, 0) + COALESCE(r.total, 0) AS display_total
    FROM wine w LEFT JOIN r ON r.wine_id = w.id
)
UPDATE wine w SET
    user_review_count = c.cnt,
    user_rating_sum   = c.total,
    user_star1_count  = c.s1, user_star2_count = c.s2, user_star3_count = c.s3,
    user_star4_count  = c.s4, user_star5_count = c.s5,
    display_review_count   = c.display_cnt,
    display_average_rating = CASE WHEN c.display_cnt = 0 THEN 0 ELSE c.display_total / c.display_cnt END,
    average_rating         = CASE WHEN c.display_cnt = 0 THEN 0 ELSE c.display_total / c.display_cnt END
FROM calc c
WHERE w.id = c.id
  AND (w.user_review_count IS DISTINCT FROM c.cnt OR w.user_rating_sum IS DISTINCT FROM c.total);

-- 2) 사용자별 리뷰 수 재계산 (users.review_count)
UPDATE users u SET review_count = COALESCE(r.cnt, 0)
FROM (SELECT u2.user_id, count(rv.id) AS cnt FROM users u2 LEFT JOIN review rv ON rv.user_id = u2.user_id GROUP BY u2.user_id) r
WHERE u.user_id = r.user_id AND u.review_count IS DISTINCT FROM r.cnt;

-- 3) UNIQUE 제약을 걸기 전에 중복이 있는지 확인 (모두 0이어야 한다. 0이 아니면 중복을 먼저 정리)
--    애플리케이션(ddl-auto=update)이 시작할 때 아래 제약을 자동으로 만든다.
--    uk_review_user_wine, uk_wine_scrap_user_wine, uk_wine_food_pairing_wine_food, uk_wine_taste_group_wine_taste, uk_food_name
SELECT 'review 중복' AS item, count(*) - count(DISTINCT (user_id, wine_id)) AS duplicates FROM review
UNION ALL SELECT 'wine_scrap 중복', count(*) - count(DISTINCT (user_id, wine_id)) FROM wine_scrap
UNION ALL SELECT 'wine_food_pairing 중복', count(*) - count(DISTINCT (wine_id, food_id)) FROM wine_food_pairing
UNION ALL SELECT 'wine_taste_group 중복', count(*) - count(DISTINCT (wine_id, taste_group_id)) FROM wine_taste_group
UNION ALL SELECT 'food 이름 중복', count(*) - count(DISTINCT name) FROM food;

-- 4) 매핑 FK NOT NULL (ddl-auto=update는 기존 컬럼의 NULL 허용 여부를 바꾸지 않으므로 직접 변경)
--    NULL인 행이 있으면 실패하므로 먼저 확인한다.
DELETE FROM wine_food_pairing WHERE wine_id IS NULL OR food_id IS NULL;
DELETE FROM wine_taste_group WHERE wine_id IS NULL OR taste_group_id IS NULL;
ALTER TABLE wine_food_pairing ALTER COLUMN wine_id SET NOT NULL, ALTER COLUMN food_id SET NOT NULL;
ALTER TABLE wine_taste_group  ALTER COLUMN wine_id SET NOT NULL, ALTER COLUMN taste_group_id SET NOT NULL;
ALTER TABLE food ALTER COLUMN name SET NOT NULL;

COMMIT;

-- 참고: 시퀀스(wine_seq 등) 보정은 schema.sql이 애플리케이션 시작 때마다 자동으로 한다.
