-- ============================================
-- Extensions
-- ============================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
  unix_ts_ms        bigint;
  ts_hex            text;

  rand_bytes        bytea;
  rand_hex          text;
  rand_a            text;
  rand_rest         text;

  variant_low2      int;
  variant_nibble    int;
  variant_hex       text;

  g1 text;
  g2 text;
  g3 text;
  g4 text;
  g5 text;
BEGIN
  -- 1) Unix epoch milliseconds → 48bit hex (12 hex chars)
  unix_ts_ms := floor(extract(epoch FROM clock_timestamp()) * 1000);
  -- 48비트만 사용하도록 12자리로 맞추고, 길면 오른쪽 12자리만 사용
  ts_hex := right(lpad(to_hex(unix_ts_ms), 12, '0'), 12);

  -- 2) 80bit 랜덤 확보 (20 hex chars)
  rand_bytes := gen_random_bytes(10);     -- 10 bytes = 80 bits
  rand_hex   := encode(rand_bytes, 'hex');

  -- rand_a: 12bit (3 hex chars)
  rand_a    := substr(rand_hex, 1, 3);
  rand_rest := substr(rand_hex, 4);       -- 나머지 17 hex chars

  -- 3) variant (10xx) + 나머지 rand_b
  -- variant 상위 2bit = 10, 하위 2bit는 랜덤 → 총 2bit randomness
  variant_low2   := get_byte(gen_random_bytes(1), 0) & 3;  -- 0..3
  variant_nibble := 8 + variant_low2;                      -- 8..11(0x8..0xB)
  variant_hex    := to_hex(variant_nibble);

  -- 4) UUID v7 각 그룹 만들기
  g1 := substr(ts_hex, 1, 8);    -- time_high (32bit)
  g2 := substr(ts_hex, 9, 4);    -- time_low  (16bit)
  g3 := '7' || rand_a;           -- version(7) + rand_a(12bit)

  -- g4: variant nibble + rand_rest 앞 3 hex (총 16bit)
  g4 := variant_hex || substr(rand_rest, 1, 3);

  -- g5: rand_rest 뒤 12 hex (총 48bit)
  g5 := substr(rand_rest, 4, 12);

  RETURN (g1 || '-' || g2 || '-' || g3 || '-' || g4 || '-' || g5)::uuid;
END;
$$;

-- =====================================================================
-- Enum Types (확장 여지: 필요 시 참조 테이블로 전환)
-- =====================================================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'subject_kind_enum') THEN
    CREATE TYPE subject_kind_enum AS ENUM ('USER','CLAN','POST','TEAM','ORG');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'asset_purpose_enum') THEN
    CREATE TYPE asset_purpose_enum AS ENUM ('PROFILE','BANNER','GALLERY','THUMBNAIL');
  END IF;
END $$;

-- =====================================================================
-- Subject Registry (프로젝션)
--  - (kind, subject_id) = PK
--  - asset_link / staging_upload_ticket 이 여기를 FK로 참조 ⇒
--    "subject_id는 kind에 종속"이 DB 레벨에서 보장됨.
-- =====================================================================
CREATE TABLE IF NOT EXISTS subject_registry (
  kind       subject_kind_enum NOT NULL,
  subject_id UUID              NOT NULL,
  alive      BOOLEAN           NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ       NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ       NOT NULL DEFAULT now(),
  PRIMARY KEY (kind, subject_id)
)
PARTITION BY LIST (kind);

-- 파티션들
CREATE TABLE IF NOT EXISTS subject_registry_user
  PARTITION OF subject_registry FOR VALUES IN ('USER');

CREATE TABLE IF NOT EXISTS subject_registry_clan
  PARTITION OF subject_registry FOR VALUES IN ('CLAN');

-- 기타 대비 기본 파티션
CREATE TABLE IF NOT EXISTS subject_registry_default
  PARTITION OF subject_registry DEFAULT;

-- 트리거
-- DROP TRIGGER IF EXISTS trg_subject_registry_upd ON subject_registry;
-- CREATE TRIGGER trg_subject_registry_upd
-- BEFORE UPDATE ON subject_registry
-- FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

-- 파티션드 인덱스
CREATE INDEX IF NOT EXISTS idx_subject_registry_kind_alive
  ON subject_registry (kind, alive);

-- =====================================================================
-- 체크 트리거: subject가 존재하고(alive=TRUE)인지 검증
--  - FK는 "존재" 보장만 함. "활성" 상태까지 강제하려면 트리거가 필요.
-- =====================================================================
CREATE OR REPLACE FUNCTION assert_subject_alive()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_alive boolean;
BEGIN
  SELECT s.alive
    INTO v_alive
    FROM subject_registry s
   WHERE s.kind = NEW.subject_kind
     AND s.subject_id = NEW.subject_id;

  IF NOT COALESCE(v_alive, false) THEN
    RAISE EXCEPTION 'Subject % (%) not alive', NEW.subject_id, NEW.subject_kind
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;



-- =====================================================================
-- Table: staging_upload_ticket
--  - 링크 전 스테이징 업로드
-- =====================================================================
CREATE TABLE IF NOT EXISTS staging_upload_ticket (
  asset_id      UUID               PRIMARY KEY,              -- 서버가 발급
  subject_kind  subject_kind_enum  NOT NULL,                 -- USER / CLAN ...
  subject_id    UUID               NOT NULL,
  purpose       asset_purpose_enum NOT NULL,                 -- PROFILE / BANNER ...
  s3_key        TEXT               NOT NULL,                 -- staging/.../{assetId}
  expires_at    TIMESTAMPTZ        NOT NULL,                 -- presign 만료와 동일(또는 조금 더 길게)
  status        TEXT               NOT NULL DEFAULT 'PENDING', -- PENDING|USED|EXPIRED
  created_at    TIMESTAMPTZ        NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ        NOT NULL DEFAULT now(),
  used_at       TIMESTAMPTZ,
  CONSTRAINT chk_staging_status CHECK (status IN ('PENDING','USED','EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_staging_ticket_subject
  ON staging_upload_ticket(subject_kind, subject_id);

CREATE INDEX IF NOT EXISTS idx_staging_ticket_expires
  ON staging_upload_ticket (expires_at);

-- FK: (kind, id) 종속 보장
ALTER TABLE staging_upload_ticket
  ADD CONSTRAINT fk_staging_subject
  FOREIGN KEY (subject_kind, subject_id)
  REFERENCES subject_registry(kind, subject_id);

-- 활성 검증 트리거
DROP TRIGGER IF EXISTS trg_staging_subject_alive ON staging_upload_ticket;
CREATE TRIGGER trg_staging_subject_alive
BEFORE INSERT OR UPDATE OF subject_kind, subject_id ON staging_upload_ticket
FOR EACH ROW EXECUTE FUNCTION assert_subject_alive();


-- =====================================================================
-- Table: image_asset
--  - 원본 바이트 해시(raw_sha256)로 전역 중복 방지 (UNIQUE)
--  - 픽셀 해시(content_sha256)로 메타(EXIF)만 다른 동일 픽셀 탐지(Index)
--  - 5MB 제한 및 이미지 크기/타입 검증은 DB에서도 보조 체크
-- =====================================================================
CREATE TABLE IF NOT EXISTS image_asset (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  raw_sha256      VARCHAR(64) NOT NULL,       -- 원본 바이트 SHA-256 (hex)
  content_sha256  VARCHAR(64) NOT NULL,       -- 픽셀 기반 SHA-256 (hex)
  content_type    TEXT        NOT NULL,       -- 'image/jpeg' | 'image/png' | 'image/webp'
  width           INT         NOT NULL,
  height          INT         NOT NULL,
  size_bytes      BIGINT      NOT NULL,

  storage_bucket  TEXT,                       -- 선택 (마이그레이션 대비)
  storage_key     TEXT        NOT NULL,       -- S3 Key
  public_url      TEXT        NOT NULL,       -- 공개/서명 URL(CDN 가능)

  exif            JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- 선택: 소량 메타 저장
  is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
  blocked_at      TIMESTAMPTZ,
  blocked_reason  TEXT,

  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ,

  -- 정합성 체크
  CONSTRAINT uq_image_asset_raw UNIQUE (raw_sha256),
  CONSTRAINT chk_image_content_type_whitelist
    CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
  CONSTRAINT chk_image_size_le_5mb
    CHECK (size_bytes > 0 AND size_bytes <= 5 * 1024 * 1024),
  CONSTRAINT chk_image_dims_positive
    CHECK (width > 0 AND height > 0),
  CONSTRAINT chk_sha256_hex
    CHECK (raw_sha256 ~ '^[0-9a-f]{64}$' AND content_sha256 ~ '^[0-9a-f]{64}$'),
  CONSTRAINT chk_deleted_means_inactive_img
    CHECK (deleted_at IS NULL OR is_active = FALSE)
);

-- updated_at 자동 갱신 트리거
-- DROP TRIGGER IF EXISTS trg_image_asset_upd ON image_asset;
-- CREATE TRIGGER trg_image_asset_upd
-- BEFORE UPDATE ON image_asset
-- FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

-- 조회/중복 탐지용 인덱스
CREATE INDEX IF NOT EXISTS idx_image_asset_created_at
  ON image_asset (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_image_asset_content_sha256
  ON image_asset (content_sha256);

CREATE INDEX IF NOT EXISTS idx_image_asset_active
  ON image_asset (is_active) WHERE deleted_at IS NULL;

-- =====================================================================
-- Table: asset_link
--  - 한 아이덴티티당 "활성" 프로필 이미지는 1개(부분 유니크)
--  - subject(kind,id) FK + 활성 검증 트리거
-- =====================================================================
CREATE TABLE IF NOT EXISTS asset_link (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subject_kind subject_kind_enum NOT NULL,     -- USER, CLAN, ...
  subject_id   UUID             NOT NULL,      -- user_id, clan_id 등
  purpose      asset_purpose_enum NOT NULL,    -- PROFILE, BANNER, ...
  asset_id     UUID             NOT NULL REFERENCES image_asset(id),

  created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
  deleted_at   TIMESTAMPTZ
);

-- DROP TRIGGER IF EXISTS trg_asset_link_upd ON asset_link;
-- CREATE TRIGGER trg_asset_link_upd
-- BEFORE UPDATE ON asset_link
-- FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

-- FK: (kind, id) 종속 보장
ALTER TABLE asset_link
  ADD CONSTRAINT fk_asset_link_subject
  FOREIGN KEY (subject_kind, subject_id)
  REFERENCES subject_registry(kind, subject_id);

-- 활성 검증 트리거 (살아있는 subject에게만 활성 링크 허용)
-- DROP TRIGGER IF EXISTS trg_asset_link_subject_alive ON asset_link;
-- CREATE TRIGGER trg_asset_link_subject_alive
-- BEFORE INSERT OR UPDATE OF subject_kind, subject_id, deleted_at ON asset_link
-- FOR EACH ROW EXECUTE FUNCTION assert_subject_alive();

-- "활성(미삭제)" 상태에서 kind+id+purpose 유니크
CREATE UNIQUE INDEX IF NOT EXISTS uq_asset_link_active_one_per_subject_purpose
  ON asset_link (subject_kind, subject_id, purpose)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_asset_link_subject
  ON asset_link (subject_kind, subject_id);

CREATE INDEX IF NOT EXISTS idx_asset_link_asset
  ON asset_link (asset_id);

-- 활성 대상에게만 활성 링크/티켓 허용 (삭제되지 않은 행 기준)
-- asset_link 쪽 - deleted_at이 NULL일 때만 체크
DROP TRIGGER IF EXISTS trg_asset_link_subject_alive ON asset_link;
CREATE TRIGGER trg_asset_link_subject_alive
BEFORE INSERT OR UPDATE ON asset_link
FOR EACH ROW
WHEN (NEW.deleted_at IS NULL)
EXECUTE FUNCTION assert_subject_alive();

-- staging_upload_ticket 쪽 - 항상 체크
DROP TRIGGER IF EXISTS trg_staging_ticket_subject_alive ON staging_upload_ticket;
CREATE TRIGGER trg_staging_ticket_subject_alive
BEFORE INSERT OR UPDATE ON staging_upload_ticket
FOR EACH ROW
EXECUTE FUNCTION assert_subject_alive();


-- =====================================================================
-- Outbox
--  - 상태 ENUM: PENDING / PROCESSING / PUBLISHED / FAILED
--  - 기본 스캔 인덱스: (status, next_attempt_at)
--  - 중복 방지: (aggregate, aggregate_id, type, version) 부분 유니크
--  - 클레임 / 성공 / 실패 유틸 함수 제공
-- =====================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'outbox_status') THEN
    CREATE TYPE outbox_status AS ENUM ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED');
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS outbox_event (
  id             UUID            PRIMARY KEY DEFAULT uuidv7(),

  aggregate      TEXT            NOT NULL,                 -- e.g., "SUBJECT", "USER", ...
  aggregate_id   UUID,                                     -- 관련 도메인 ID (없으면 NULL 허용)
  type           TEXT            NOT NULL,                 -- e.g., "SubjectUpsert"
  version        BIGINT          NOT NULL DEFAULT 0,       -- 동일 이벤트 타입의 버전(중복 방지 키 구성)

  payload        JSONB           NOT NULL,                 -- 이벤트 본문
  headers        JSONB           NOT NULL DEFAULT '{}'::jsonb, -- 메타(스키마/소스 등)

  status         outbox_status   NOT NULL DEFAULT 'PENDING',
  attempts       INT             NOT NULL DEFAULT 0,
  last_error     TEXT,

  next_attempt_at TIMESTAMPTZ    NOT NULL DEFAULT now(),  -- 재시도 시각
  locked_by      TEXT,                                     -- 클레임한 컨슈머 ID
  locked_until   TIMESTAMPTZ,                              -- 잠금 만료(세이프가드)

  created_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ,
  deleted_at     TIMESTAMPTZ,

  -- PROCESSING 상태면 잠금 정보가 있어야 함(최소한의 일관성 체크)
  CONSTRAINT chk_outbox_processing_has_lock
    CHECK (status <> 'PROCESSING' OR (locked_until IS NOT NULL AND locked_by IS NOT NULL))
);

-- 중복 방지(미삭제 기준) 키: 같은 aggregate/aggregate_id/type/version의 중복 삽입 방지
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_aggregate_version
  ON outbox_event (aggregate, aggregate_id, type, version)
  WHERE deleted_at IS NULL;

-- 컨슈머 스캔 인덱스: 상태 + 시각
CREATE INDEX IF NOT EXISTS idx_outbox_status_next_attempt
  ON outbox_event (status, next_attempt_at, created_at)
  INCLUDE (attempts, type, aggregate, aggregate_id);

-- 상태/시간 기반 조회 보조 인덱스(옵션)
CREATE INDEX IF NOT EXISTS idx_outbox_created
  ON outbox_event (created_at DESC);

-- =====================================================================
-- Outbox 유틸 함수
-- =====================================================================

-- 배치 클레임: PENDING & next_attempt_at <= now() 인 레코드를 SKIP LOCKED로 확보하고
-- PROCESSING 상태로 전환 + 잠금/시도수 증가
--  - p_consumer: 컨슈머 식별자(호스트명/인스턴스ID 등)
--  - p_batch   : 가져올 개수
--  - p_lock_sec: 잠금 유지 초(타임아웃 대비)
DO $$
BEGIN
  IF to_regprocedure('outbox_pull(text, integer, integer)') IS NULL THEN
    CREATE FUNCTION outbox_pull(p_consumer TEXT, p_batch INTEGER, p_lock_sec INTEGER DEFAULT 60)
    RETURNS SETOF outbox_event
    LANGUAGE plpgsql
    AS $FN$
    BEGIN
      RETURN QUERY
      WITH cte AS (
        SELECT id
        FROM outbox_event
        WHERE status = 'PENDING'
          AND next_attempt_at <= now()
        ORDER BY created_at
        FOR UPDATE SKIP LOCKED
        LIMIT p_batch
      )
      UPDATE outbox_event o
         SET status       = 'PROCESSING',
             locked_by    = p_consumer,
             locked_until = now() + make_interval(secs => p_lock_sec),
             attempts     = o.attempts + 1,
             updated_at   = now()
      FROM cte
      WHERE o.id = cte.id
      RETURNING o.*;
    END;
    $FN$;
  END IF;
END $$;

-- 성공 처리: PUBLISHED로 마킹하고 잠금 해제
DO $$
BEGIN
  IF to_regprocedure('outbox_succeed(uuid, text)') IS NULL THEN
    CREATE FUNCTION outbox_succeed(p_id UUID, p_consumer TEXT)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
    AS $FN$
    DECLARE v_cnt INT;
    BEGIN
      UPDATE outbox_event
         SET status       = 'PUBLISHED',
             published_at = now(),
             locked_by    = NULL,
             locked_until = NULL,
             last_error   = NULL,
             updated_at   = now()
       WHERE id = p_id
         AND status = 'PROCESSING'
         AND locked_by = p_consumer;
      GET DIAGNOSTICS v_cnt = ROW_COUNT;
      RETURN v_cnt > 0;
    END;
    $FN$;
  END IF;
END $$;

-- 실패 처리: 에러 기록 + backoff 후 재시도(PENDING) 혹은 FAILED 전환
--  - p_retry_sec   : 기본 재시도 초
--  - p_max_attempts: 이 횟수에 도달하면 FAILED로 전환
--  - p_backoff_mul : 시도수에 따른 선형 계수(간단 버전: retry = p_retry_sec * attempts * p_backoff_mul)
DO $$
BEGIN
  IF to_regprocedure('outbox_fail(uuid, text, text, integer, integer, numeric)') IS NULL THEN
    CREATE FUNCTION outbox_fail(p_id UUID, p_error TEXT, p_consumer TEXT,
                                p_retry_sec INTEGER DEFAULT 30,
                                p_max_attempts INTEGER DEFAULT 20,
                                p_backoff_mul NUMERIC DEFAULT 1.0)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
    AS $FN$
    DECLARE
      v_attempts INT;
      v_next     TIMESTAMPTZ;
      v_final    BOOLEAN;
      v_cnt      INT;
    BEGIN
      SELECT attempts INTO v_attempts FROM outbox_event
      WHERE id = p_id AND status = 'PROCESSING' AND locked_by = p_consumer
      FOR UPDATE;
      IF NOT FOUND THEN
        RETURN FALSE;
      END IF;

      v_final := (v_attempts >= p_max_attempts);

      IF v_final THEN
        UPDATE outbox_event
           SET status        = 'FAILED',
               last_error    = p_error,
               locked_by     = NULL,
               locked_until  = NULL,
               updated_at    = now()
         WHERE id = p_id;
        GET DIAGNOSTICS v_cnt = ROW_COUNT;
        RETURN v_cnt > 0;
      ELSE
        UPDATE outbox_event
           SET status          = 'PENDING',
               next_attempt_at = now() + make_interval(secs => GREATEST(1, p_retry_sec * v_attempts * p_backoff_mul)),
               last_error      = p_error,
               locked_by       = NULL,
               locked_until    = NULL,
               updated_at      = now()
         WHERE id = p_id;
        GET DIAGNOSTICS v_cnt = ROW_COUNT;
        RETURN v_cnt > 0;
      END IF;
    END;
    $FN$;
  END IF;
END $$;

DO $$
BEGIN
  IF to_regprocedure('outbox_requeue_timeouts(integer)') IS NULL THEN
    CREATE FUNCTION outbox_requeue_timeouts(p_grace_sec INTEGER DEFAULT 0)
    RETURNS BIGINT
    LANGUAGE plpgsql
    AS $FN$
    DECLARE v_cnt BIGINT;
    BEGIN
      UPDATE outbox_event
         SET status          = 'PENDING',
             locked_by       = NULL,
             locked_until    = NULL,
             next_attempt_at = now(),
             updated_at      = now()
       WHERE status = 'PROCESSING'
         AND locked_until IS NOT NULL
         AND locked_until < (now() - make_interval(secs => p_grace_sec));
      GET DIAGNOSTICS v_cnt = ROW_COUNT;
      RETURN v_cnt;
    END;
    $FN$;
  END IF;
END $$;

-- (옵션) 보존기간 지난 PUBLISHED/FAILED 정리
DO $$
BEGIN
  IF to_regprocedure('purge_outbox(INTEGER)') IS NULL THEN
    CREATE FUNCTION purge_outbox(days INTEGER)
    RETURNS BIGINT
    LANGUAGE plpgsql
    AS $FN$
    DECLARE
      v_cnt BIGINT;
    BEGIN
      DELETE FROM outbox_event
       WHERE (status IN ('PUBLISHED','FAILED'))
         AND coalesce(published_at, updated_at, created_at) < (now() - make_interval(days => days));
      GET DIAGNOSTICS v_cnt = ROW_COUNT;
      RETURN v_cnt;
    END;
    $FN$;
  END IF;
END $$;