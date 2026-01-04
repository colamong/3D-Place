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
  -- 클랜 가입 정책: 누구나 / 승인제 / 초대 전용
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'clan_join_policy_enum') THEN
    CREATE TYPE clan_join_policy_enum AS ENUM ('OPEN','APPROVAL','INVITE_ONLY');
  END IF;

  -- 멤버 역할: 길드장 / 부길드장 / 일반
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'clan_member_role_enum') THEN
    CREATE TYPE clan_member_role_enum AS ENUM ('MASTER','OFFICER','MEMBER');
  END IF;

  -- 멤버 상태: 활성 / 초대만 / 대기 / 탈퇴 / 추방 / 차단
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'clan_member_status_enum') THEN
    CREATE TYPE clan_member_status_enum AS ENUM ('ACTIVE','INVITED','PENDING','LEFT','KICKED','BANNED');
  END IF;

  -- 가입 요청 상태
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'clan_join_request_status_enum') THEN
    CREATE TYPE clan_join_request_status_enum AS ENUM ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED');
  END IF;
END $$;

-- =========================================================
-- clan_info: 클랜(길드) 정보
--  - subject_kind_enum 에 'CLAN' 이미 있다고 가정
--  - owner_id 는 user_account(id) FK 가정
-- =========================================================
CREATE TABLE IF NOT EXISTS clan_info (
  id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  handle        CITEXT        NOT NULL,      -- 고유 핸들
  name          TEXT          NOT NULL,      -- 표시 이름
  description   TEXT,
  owner_id      UUID          NOT NULL,      -- 클랜장 (user_account.id)

  join_policy   clan_join_policy_enum NOT NULL DEFAULT 'OPEN',
  is_public     BOOLEAN       NOT NULL DEFAULT TRUE,    -- 리스트/검색 노출 여부

  member_count  INTEGER       NOT NULL DEFAULT 0 CHECK (member_count >= 0),
  paint_count_total BIGINT    NOT NULL DEFAULT 0 CHECK (paint_count_total >= 0),
  metadata      JSONB         NOT NULL DEFAULT '{}'::jsonb,

  created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  deleted_at    TIMESTAMPTZ,

  CONSTRAINT uq_clan_handle UNIQUE (handle)
);

-- 자주 쓰는 인덱스
CREATE INDEX IF NOT EXISTS idx_clan_owner_id_active
  ON clan_info(owner_id);

-- =========================================================
-- clan_member: 클랜멤버 정보
--  - 한 유저는 하나의 클랜에만 가입 가능하게 설계
-- =========================================================
CREATE TABLE IF NOT EXISTS clan_member (
  id          UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
  clan_id     UUID                    NOT NULL,
  user_id     UUID                    NOT NULL,
  role        clan_member_role_enum   NOT NULL DEFAULT 'MEMBER',
  status      clan_member_status_enum NOT NULL DEFAULT 'ACTIVE',

  paint_count_total BIGINT    NOT NULL DEFAULT 0 CHECK (paint_count_total >= 0),

  joined_at   TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
  left_at     TIMESTAMPTZ,

  created_at  TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ,

  CONSTRAINT fk_clan_member_clan FOREIGN KEY (clan_id)
    REFERENCES clan_info(id)
);

-- 한 클랜에서 같은 유저는 1개 멤버십만 (soft delete 제외)
CREATE UNIQUE INDEX IF NOT EXISTS uq_clan_member_active
  ON clan_member(clan_id, user_id)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_clan_member_active_user
  ON clan_member(user_id)
  WHERE status = 'ACTIVE' AND deleted_at IS NULL;
  
CREATE INDEX IF NOT EXISTS idx_clan_member_user_active
  ON clan_member(user_id)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_clan_member_clan_active
  ON clan_member(clan_id)
  WHERE deleted_at IS NULL;

-- =========================================================
-- clan_join_request: 클랜 가입 요청
--  - OPEN 클랜은 바로 가입 처리하고 로그용으로만 쓸 수도 있음
-- =========================================================
CREATE TABLE IF NOT EXISTS clan_join_request (
  id           UUID                             PRIMARY KEY DEFAULT gen_random_uuid(),
  clan_id      UUID                             NOT NULL,
  user_id      UUID                             NOT NULL, -- 요청자
  status       clan_join_request_status_enum    NOT NULL DEFAULT 'PENDING',

  message      TEXT,        -- 지원 메시지
  reviewer_id  UUID,        -- 승인/거절 처리한 운영자/클랜 관리자 (user_account.id)
  decided_at   TIMESTAMPTZ, -- 승인/거절 시각

  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  deleted_at   TIMESTAMPTZ,

  CONSTRAINT fk_clan_join_request_clan FOREIGN KEY (clan_id)
    REFERENCES clan_info(id)
);

-- 같은 클랜에 같은 유저가 동시에 여러 PENDING 요청을 못 만들도록
CREATE UNIQUE INDEX IF NOT EXISTS uq_clan_join_request_pending
  ON clan_join_request(clan_id, user_id)
  WHERE status = 'PENDING' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_clan_join_request_clan
  ON clan_join_request(clan_id, status);

CREATE INDEX IF NOT EXISTS idx_clan_join_request_user
  ON clan_join_request(user_id, status);

-- =========================================================
-- clan_invite_ticket: 클랜 초대 링크 티켓
--  - code: URL에 들어가는 랜덤 토큰
--  - expires_at: 생성 시각 + 12시간 (DB 함수에서 설정)
--  - max_uses: 0 = 무제한, >0 이면 해당 횟수까지만 허용
-- =========================================================
CREATE TABLE IF NOT EXISTS clan_invite_ticket (
  id           UUID         PRIMARY KEY DEFAULT uuidv7(),

  clan_id      UUID         NOT NULL,
  code         TEXT         NOT NULL,    -- URL-safe 랜덤 토큰
  created_by   UUID         NOT NULL,    -- 발급한 유저 (user_account.id, FK는 서비스 분리 고려해서 생략)

  max_uses     INTEGER      NOT NULL DEFAULT 0,           -- 0 = unlimited
  use_count    INTEGER      NOT NULL DEFAULT 0,

  expires_at   TIMESTAMPTZ  NOT NULL,                     -- now() + interval '12 hours'
  is_revoked   BOOLEAN      NOT NULL DEFAULT FALSE,       -- 강제 만료

  metadata     JSONB        NOT NULL DEFAULT '{}'::jsonb, -- 확장용 (예: 발급 사유, 메모 등)

  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at   TIMESTAMPTZ,

  CONSTRAINT fk_clan_invite_ticket_clan
    FOREIGN KEY (clan_id) REFERENCES clan_info(id),

  CONSTRAINT uq_clan_invite_ticket_code UNIQUE (code),

  CONSTRAINT chk_clan_invite_ticket_use_count CHECK (use_count >= 0),
  CONSTRAINT chk_clan_invite_ticket_max_uses CHECK (max_uses >= 0),
  CONSTRAINT chk_clan_invite_ticket_expiry CHECK (expires_at > created_at)
);

-- code로 바로 조회할 일이 많으니 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_clan_invite_ticket_code
  ON clan_invite_ticket(code);

-- 클랜별 유효 티켓 조회용 인덱스 (만료/삭제/취소 여부는 WHERE에서 거름)
CREATE INDEX IF NOT EXISTS idx_clan_invite_ticket_clan_active
  ON clan_invite_ticket(clan_id, expires_at)
  WHERE deleted_at IS NULL AND is_revoked = FALSE;


-- 초대 토큰 발급 함수
DO $$
BEGIN
  IF to_regprocedure('create_clan_invite_ticket(uuid, uuid, integer)') IS NULL THEN
    CREATE FUNCTION create_clan_invite_ticket(
      p_clan_id    UUID,
      p_created_by UUID,
      p_max_uses   INTEGER DEFAULT 0  -- 0 = unlimited
    )
    RETURNS clan_invite_ticket
    LANGUAGE plpgsql
    AS $FN$
    DECLARE
      v_ticket clan_invite_ticket;
      v_code   TEXT;
    BEGIN
      -- 랜덤 토큰 생성 (hex 32자) - media 업로드 티켓과 동일 패턴 가정
      v_code := encode(gen_random_bytes(16), 'hex');

      INSERT INTO clan_invite_ticket (
        clan_id,
        code,
        created_by,
        max_uses,
        expires_at
      )
      VALUES (
        p_clan_id,
        v_code,
        p_created_by,
        COALESCE(p_max_uses, 0),
        now() + interval '12 hours'
      )
      RETURNING * INTO v_ticket;

      RETURN v_ticket;
    END;
    $FN$;
  END IF;
END $$;

-- 초대 소비 함수
DO $$
BEGIN
  IF to_regprocedure('consume_clan_invite_ticket(text)') IS NULL THEN
    CREATE FUNCTION consume_clan_invite_ticket(
      p_code TEXT
    )
    RETURNS clan_invite_ticket
    LANGUAGE plpgsql
    AS $FN$
    DECLARE
      v_ticket clan_invite_ticket;
    BEGIN
      -- 티켓 로딩 & 락
      SELECT *
      INTO v_ticket
      FROM clan_invite_ticket
      WHERE code = p_code
        AND deleted_at IS NULL
        AND is_revoked = FALSE
      FOR UPDATE;

      IF NOT FOUND THEN
        RAISE EXCEPTION 'CLAN_INVITE_NOT_FOUND'
          USING ERRCODE = 'P0002';
      END IF;

      -- 만료 체크 (12시간 TTL)
      IF v_ticket.expires_at <= now() THEN
        RAISE EXCEPTION 'CLAN_INVITE_EXPIRED'
          USING ERRCODE = '22023';
      END IF;

      -- 사용 횟수 제한 체크 (0 = 무제한)
      IF v_ticket.max_uses > 0 AND v_ticket.use_count >= v_ticket.max_uses THEN
        RAISE EXCEPTION 'CLAN_INVITE_EXHAUSTED'
          USING ERRCODE = '22023';
      END IF;

      -- use_count 증가
      UPDATE clan_invite_ticket
         SET use_count = use_count + 1,
             updated_at = now()
       WHERE id = v_ticket.id;

      v_ticket.use_count := v_ticket.use_count + 1;

      RETURN v_ticket;
    END;
    $FN$;
  END IF;
END $$;

-- 만료된 초대 티켓 일괄 만료 처리 (is_revoked = TRUE)
DO $$
BEGIN
  IF to_regprocedure('expire_clan_invite_tickets(integer)') IS NULL THEN
    CREATE FUNCTION expire_clan_invite_tickets(p_grace_sec INTEGER DEFAULT 0)
    RETURNS BIGINT
    LANGUAGE plpgsql
    AS $FN$
    DECLARE
      v_cnt BIGINT;
    BEGIN
      UPDATE clan_invite_ticket
         SET is_revoked = TRUE,
             updated_at = now()
       WHERE deleted_at IS NULL
         AND is_revoked = FALSE
         AND expires_at < (now() - make_interval(secs => p_grace_sec));

      GET DIAGNOSTICS v_cnt = ROW_COUNT;
      RETURN v_cnt;
    END;
    $FN$;
  END IF;
END $$;

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