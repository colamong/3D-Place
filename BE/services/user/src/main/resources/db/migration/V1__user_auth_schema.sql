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
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'auth_provider') THEN
    CREATE TYPE auth_provider AS ENUM ('AUTH0', 'GOOGLE', 'KAKAO', 'GUEST');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'auth_event_kind') THEN
    CREATE TYPE auth_event_kind AS ENUM ('SIGNUP','LOGIN','REFRESH','LOGOUT','LINK','UNLINK','SYNC');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'account_role') THEN
    CREATE TYPE account_role AS ENUM ('USER', 'ADMIN', 'MODERATOR');
  END IF;
END $$;

-- =====================================================================
-- Common trigger function
-- =====================================================================
DO $$
BEGIN
  IF to_regprocedure('normalize_account_roles(account_role[])') IS NULL THEN
    CREATE FUNCTION normalize_account_roles(a account_role[])
    RETURNS account_role[] AS $FN$
      SELECT COALESCE(
               (SELECT array_agg(DISTINCT e ORDER BY e)
                  FROM unnest(a) AS t(e)),
               ARRAY[]::account_role[]
             );
    $FN$ LANGUAGE sql IMMUTABLE STRICT;
  END IF;

  IF to_regprocedure('user_account_roles_biu()') IS NULL THEN
    CREATE FUNCTION user_account_roles_biu()
    RETURNS trigger AS $FN$
    BEGIN
      NEW.roles := normalize_account_roles(NEW.roles);
      RETURN NEW;
    END;
    $FN$ LANGUAGE plpgsql;
  END IF;
END $$;

-- =====================================================================
-- Table: nickname_series (닉네임별 다음 seq 관리)
-- =====================================================================
CREATE TABLE IF NOT EXISTS nickname_series (
  nickname TEXT PRIMARY KEY,
  next_seq INTEGER NOT NULL DEFAULT 1
);

-- =====================================================================
-- Table: user_account
--  - 이메일은 게스트를 위해 NULL 허용
--  - 소프트삭제 + 부분 유니크 인덱스 전략
--  - nickname_seq는 닉네임별 일련번호, nickname_handle은 자동 생성
-- =====================================================================
CREATE TABLE IF NOT EXISTS user_account (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email           CITEXT,                          -- NULL 가능(게스트)
  nickname        TEXT,
  nickname_seq    INTEGER,
  nickname_handle TEXT GENERATED ALWAYS AS (
    CASE
      WHEN nickname IS NULL OR nickname_seq IS NULL THEN NULL
      ELSE nickname || '#' || lpad(nickname_seq::text, 4, '0')
    END
  ) STORED,
  roles           account_role[] NOT NULL DEFAULT ARRAY['USER']::account_role[],
  is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
  email_verified  BOOLEAN     NOT NULL DEFAULT FALSE,
  
  paint_count_total BIGINT    NOT NULL DEFAULT 0 CHECK (paint_count_total >= 0),
  metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,

  last_login_at   TIMESTAMPTZ,
  login_count     BIGINT      NOT NULL DEFAULT 0,

  blocked_at      TIMESTAMPTZ,
  blocked_reason  TEXT,

  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ,

  -- 정합성 체크
  CONSTRAINT chk_email_verified_requires_email
    CHECK (NOT email_verified OR email IS NOT NULL),

  CONSTRAINT chk_deleted_means_inactive
    CHECK (deleted_at IS NULL OR is_active = FALSE),

  CONSTRAINT chk_nickname_seq_requires_nickname
    CHECK (nickname_seq IS NULL OR nickname IS NOT NULL),

  CONSTRAINT chk_nickname_seq_range
    CHECK (nickname_seq BETWEEN 0 AND 9999),

  CONSTRAINT chk_roles_canonical_and_not_empty
    CHECK (roles = normalize_account_roles(roles) AND array_length(roles, 1) >= 1)
);

-- updated_at 트리거
-- DROP TRIGGER IF EXISTS trg_user_account_upd ON user_account;
-- CREATE TRIGGER trg_user_account_upd
--   BEFORE UPDATE ON user_account
--   FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

-- INSERT/UPDATE 시 roles 자동 정규화
DROP TRIGGER IF EXISTS trg_user_account_roles_biu ON user_account;
CREATE TRIGGER trg_user_account_roles_biu
  BEFORE INSERT OR UPDATE OF roles ON user_account
  FOR EACH ROW EXECUTE FUNCTION user_account_roles_biu();

-- 부분 유니크(소프트삭제 안전): 삭제되지 않은 레코드에서만 email 고유
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_account_email_alive
  ON user_account (email)
  WHERE deleted_at IS NULL;

-- 닉네임(+seq) 고유: 활성(미삭제) 사용자 기준
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_nickname_alive
  ON user_account ((lower(nickname)), nickname_seq)
  WHERE deleted_at IS NULL;

-- 조회/검색 인덱스
CREATE INDEX IF NOT EXISTS idx_user_account_created_at
  ON user_account (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_account_last_login
  ON user_account (last_login_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_account_metadata_gin
  ON user_account USING GIN (metadata);
  
CREATE INDEX IF NOT EXISTS idx_user_account_roles_gin
  ON user_account USING GIN (roles);

-- =====================================================================
-- Trigger Function: 닉네임별 seq 자동 부여
--  - INSERT 시 nickname_seq 비어있으면 부여
--  - UPDATE 로 nickname 변경 시, 새 닉네임 기준으로 새 seq 부여
--  - 번호는 재사용되지 않음(모노톤 증가)
-- =====================================================================
CREATE OR REPLACE FUNCTION assign_nickname_seq()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_seq INTEGER;
  v_key citext;
BEGIN
  IF NEW.nickname IS NULL THEN
    RETURN NEW; -- 닉네임 없으면 패스
  END IF;

  v_key := NEW.nickname::citext;

  IF (TG_OP = 'INSERT' AND NEW.nickname_seq IS NULL)
     OR (TG_OP = 'UPDATE' AND NEW.nickname IS DISTINCT FROM OLD.nickname AND NEW.nickname_seq IS NULL) THEN

    WITH s AS (
      INSERT INTO nickname_series (nickname, next_seq)
      VALUES (v_key, 2)
      ON CONFLICT (nickname) DO UPDATE
        SET next_seq = nickname_series.next_seq + 1
      RETURNING next_seq
    )
    SELECT (next_seq - 1) INTO v_seq FROM s;

    NEW.nickname_seq := v_seq;
  END IF;

  RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_assign_nickname_seq ON user_account;
CREATE TRIGGER trg_assign_nickname_seq
  BEFORE INSERT OR UPDATE OF nickname ON user_account
  FOR EACH ROW
  EXECUTE FUNCTION assign_nickname_seq();

-- =====================================================================
-- Table: user_identity (외부 IdP 매핑)
--  - (provider, tenant, sub) 조합 유니크(소프트삭제 안전)
-- =====================================================================
CREATE TABLE IF NOT EXISTS user_identity (
  id                       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                  UUID           NOT NULL REFERENCES user_account(id),
  provider                 auth_provider  NOT NULL,
  provider_tenant          TEXT           NOT NULL,
  provider_sub             TEXT           NOT NULL,

  email_at_provider        CITEXT,
  display_name_at_provider TEXT,
  last_sync_at             TIMESTAMPTZ,

  created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
  deleted_at               TIMESTAMPTZ
);

-- updated_at 트리거
-- DROP TRIGGER IF EXISTS trg_user_identity_upd ON user_identity;
-- CREATE TRIGGER trg_user_identity_upd
--   BEFORE UPDATE ON user_identity
--   FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

-- 소프트삭제 안전 유니크
CREATE UNIQUE INDEX IF NOT EXISTS uq_identity_provider_tuple_alive
  ON user_identity (provider, provider_tenant, provider_sub)
  WHERE deleted_at IS NULL;

-- 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_user_identity_user_id
  ON user_identity (user_id);

CREATE INDEX IF NOT EXISTS idx_user_identity_provider
  ON user_identity (provider, provider_tenant);

-- =====================================================================
-- Table: auth_event (대용량 이벤트 → 월 단위 파티셔닝)
--  - 비즈니스 로직은 앱에서; DB는 저장/청소와 조회 최적화만
-- =====================================================================
CREATE TABLE IF NOT EXISTS auth_event (
  id          UUID            NOT NULL DEFAULT uuidv7(),
  user_id     UUID            NOT NULL REFERENCES user_account(id) ON DELETE RESTRICT,
  provider    auth_provider,
  kind        auth_event_kind NOT NULL,
  detail      TEXT,
  ip_addr     INET,
  user_agent  TEXT,

  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  deleted_at  TIMESTAMPTZ,
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX IF NOT EXISTS idx_auth_event_uuid ON auth_event (id);

-- 부모 테이블에 인덱스 정의(각 파티션에 전파)
CREATE INDEX IF NOT EXISTS idx_auth_event_user_created_uuid_live
  ON auth_event (id, created_at DESC, id DESC)
  INCLUDE (provider, kind, detail, ip_addr, user_agent)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_event_kind_time
  ON auth_event (kind, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_event_provider_time
  ON auth_event (provider, created_at DESC);

-- updated_at 트리거(부모에 달면 파티션에도 적용)
-- DROP TRIGGER IF EXISTS trg_auth_event_upd ON auth_event;
-- CREATE TRIGGER trg_auth_event_upd
--   BEFORE UPDATE ON auth_event
--   FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

-- ============================================================================
-- 파티션 생성 유틸 (월 단위)
-- ============================================================================
CREATE OR REPLACE FUNCTION create_auth_event_partition(p_year INT, p_month INT)
RETURNS VOID AS $$
DECLARE
  start_date DATE := make_date(p_year, p_month, 1);
  end_date   DATE := (make_date(p_year, p_month, 1) + INTERVAL '1 month')::date;
  part_name  TEXT := format('auth_event_%s_%s', p_year, lpad(p_month::text, 2, '0'));
  ddl TEXT;
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('r','p') 
      AND n.nspname = ANY (current_schemas(true))
      AND c.relname = part_name
  ) THEN
    RETURN;
  END IF;

  ddl := format($f$
    CREATE TABLE %I
      PARTITION OF auth_event
      FOR VALUES FROM (%L) TO (%L);
  $f$, part_name, start_date, end_date);

  EXECUTE ddl;
END;
$$ LANGUAGE plpgsql;



CREATE OR REPLACE FUNCTION ensure_auth_event_partitions(p_from DATE, p_to DATE)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    d DATE := date_trunc('month', p_from)::date;
    stop_at DATE := date_trunc('month', p_to)::date;
BEGIN
    IF d >= stop_at THEN
        stop_at := (d + INTERVAL '1 month')::date;
    END IF;

    WHILE d < stop_at LOOP
        PERFORM create_auth_event_partition(
            EXTRACT(YEAR FROM d)::int,
            EXTRACT(MONTH FROM d)::int
        );
        d := (d + INTERVAL '1 month')::date;
    END LOOP;
END;
$$;

-- ============================================================================
-- 오래된 파티션 드롭
-- ============================================================================
CREATE OR REPLACE FUNCTION drop_old_auth_event_partitions(keep_months INT)
RETURNS VOID AS $$
DECLARE
  cutoff DATE := (date_trunc('month', now()) - make_interval(months => keep_months))::date;
  part RECORD;
BEGIN
  FOR part IN
    SELECT inhrelid::regclass::text AS child
    FROM   pg_inherits
    WHERE  inhparent = 'auth_event'::regclass
  LOOP
    IF part.child ~ '^auth_event_[0-9]{4}_[0-9]{2}$' THEN
      PERFORM 1;
      BEGIN
        IF to_date(substr(part.child, 12, 7), 'YYYY_MM') < cutoff THEN
          EXECUTE format('DROP TABLE IF EXISTS %I;', part.child);
        END IF;
      EXCEPTION WHEN others THEN
        RAISE NOTICE 'Skip dropping % due to %', part.child, SQLERRM;
      END;
    END IF;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT create_auth_event_partition(date_part('year', now())::int, date_part('month', now())::int);


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

-- updated_at 트리거
-- DROP TRIGGER IF EXISTS trg_outbox_upd ON outbox_event;
-- CREATE TRIGGER trg_outbox_upd
--   BEFORE UPDATE ON outbox_event
--   FOR EACH ROW EXECUTE FUNCTION set_timestamp_mod();

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