-- src/main/resources/schema.sql

-- 1. 확장 모듈 설치 (없으면 생성)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 컬럼 추가 (JPA ddl-auto가 실패하거나 확장이 먼저 필요한 경우 대비)
ALTER TABLE wine ADD COLUMN IF NOT EXISTS embedding vector(1536);

-- 3. 인덱스 생성 (없으면 생성)
CREATE INDEX IF NOT EXISTS idx_wine_name_kr_trgm ON wine USING gin (name_kr gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_wine_embedding ON wine USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 4. JPA SEQUENCE 보정 (와인·매핑 엔티티를 IDENTITY → SEQUENCE로 바꾼 뒤 JDBC 배치 INSERT 사용)
-- Hibernate가 시퀀스를 1부터 만들면 기존 ID와 겹치므로, 시작할 때마다 시퀀스를 기존 최대 ID 이상으로 맞춘다.
-- allocationSize=50(pooled)에서는 nextval 값이 할당 범위의 끝이므로 setval(최대 ID)이면 다음 범위가 최대 ID+1부터 시작한다.
-- 이미 시퀀스가 더 앞서 있으면(GREATEST) 되돌리지 않는다.
CREATE SEQUENCE IF NOT EXISTS wine_seq START WITH 1 INCREMENT BY 50;
SELECT setval('wine_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM wine), (SELECT last_value FROM wine_seq)));
CREATE SEQUENCE IF NOT EXISTS wine_food_pairing_seq START WITH 1 INCREMENT BY 50;
SELECT setval('wine_food_pairing_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM wine_food_pairing), (SELECT last_value FROM wine_food_pairing_seq)));
CREATE SEQUENCE IF NOT EXISTS wine_taste_group_seq START WITH 1 INCREMENT BY 50;
SELECT setval('wine_taste_group_seq', GREATEST((SELECT COALESCE(MAX(wine_taste_group_id), 1) FROM wine_taste_group), (SELECT last_value FROM wine_taste_group_seq)));