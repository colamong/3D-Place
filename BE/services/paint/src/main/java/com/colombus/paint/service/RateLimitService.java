package com.colombus.paint.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.colombus.paint.exception.PaintErrorCode;
import com.colombus.paint.exception.PaintRateLimitException;

import reactor.core.publisher.Mono;

/**
 * - paint/erase 합산 64개
 * - 1분당 1개씩 계단식 자동회복
 * - 배치(txId)별로 롤백 처리(이번에 추가한 요청만 삭제)
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

	private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

	private final ReactiveStringRedisTemplate redisTemplate;

	private static final int USER_MAX_TOKENS = 64;
	private static final long REFILL_RATE_MS = 5000L; // 5초

	/*
	 * KEYS[1] = slot_key (rate_limit:slots:{actor}) → 메인 슬롯 저장소 (Sorted Set)
	 * KEYS[2] = rollback_key (rate_limit:rollback:{actor}:{txId}) → 롤백용 추적 명단 (일반 Set)
	 * ARGV[1] = max_slots (64)
	 * ARGV[2] = requested (요청한 슬롯 수)
	 * ARGV[3] = now (현재시간 ms)
	 * ARGV[4] = refill_ms (5000)
	 *
	 * 반환값:
	 * 양수: 작업중인 슬롯의 개수 (성공)
	 * -1: 슬롯 부족 (실패)
	 */
	private static final String LUA_CONSUME_WITH_TX = """
	local slot_key       = KEYS[1]
	local rollback_key 	 = KEYS[2]
	local max_slots = tonumber(ARGV[1])
	local requested = tonumber(ARGV[2])
	local now       = tonumber(ARGV[3])
	local refill_ms = tonumber(ARGV[4])
	
	-- 0부터 현재까지, 만료된 slot을 score로 정렬하여 삭제
	redis.call('ZREMRANGEBYSCORE', slot_key, 0, now)
	
	-- 현재 활성(아직 살아있는 슬롯 개수, 현재 초과 ~ 무한)
	local active = redis.call('ZCOUNT', slot_key, '(' .. now, '+inf')
	
	local available = max_slots - active
	if available < requested then
	return -1
	end
	
	-- args = [expiry1, member1, expiry2, member2, ...]
	local args = {}
	for i = 1, requested do
	local expiry = now + (i * refill_ms)
	
	-- 고유한 slot_id(시간:순서:난수)를 생성해서 member 라는 변수에 저장
	local member = tostring(now) .. ":" .. tostring(i) .. ":" .. tostring(math.random(1000000))
	table.insert(args, expiry)
	table.insert(args, member)
	redis.call('SADD', rollback_key, member)
	end
	
	redis.call('ZADD', slot_key, unpack(args))
	
	-- TTL 설정
	redis.call('EXPIRE', slot_key, 86400)
	redis.call('EXPIRE', rollback_key, 3600)
	
	-- 작업중인 슬롯의 개수
	return tonumber(active) + requested
	""";

	private final RedisScript<Long> luaConsumeWithTx =
		new DefaultRedisScript<>(LUA_CONSUME_WITH_TX, Long.class);

	/*
	 * KEYS[1] = slot_key
	 * KEYS[2] = rollback_key
	 * ARGV    = none
	 *
	 * return: 롤백된 멤버(슬롯)의 개수
	 */
	private static final String LUA_ROLLBACK_TX = """
	local slot_key 	   = KEYS[1]
	local rollback_key = KEYS[2]
	
	-- 이번 트랜잭션에서 추가한 슬롯 ID 가져오기
	local members = redis.call('SMEMBERS', rollback_key)
	
	-- slot_key 에서 해당 슬롯들만 제거(롤백)
	local removed = 0
	if #members > 0 then
	redis.call('ZREM', slot_key, unpack(members))
	removed = #members
	end
	
	-- rollback_key 삭제(롤백)
	redis.call('DEL', rollback_key)
	
	return removed
	""";

	private final RedisScript<Long> luaRollbackTx =
		new DefaultRedisScript<>(LUA_ROLLBACK_TX, Long.class);

	/*
	 * 성공했으면 rollback_key 삭제
	 *
	 * slot_key는 만료시까지 유지
	 * rollback_key만 삭제(성공해서 롤백이 필요없기 때문)
	 *
	 * KEYS[1] = rollback_key
	 */
	private static final String LUA_COMMIT_TX = """
	local rollback_key = KEYS[1]
	redis.call('DEL', rollback_key)
	return 1
	""";

	private final RedisScript<Long> luaCommitTx =
		new DefaultRedisScript<>(LUA_COMMIT_TX, Long.class);

	/**
	 * 배치 슬롯 소비 시작
	 *
	 * 동작:
	 * 1. 고유한 트랜잭션 ID(txId) 생성
	 * 2. Lua 스크립트로 슬롯 소비 시도
	 * 3. 성공 시 txId 반환, 실패 시 에러
	 *
	 * @param actor 사용자 ID
	 * @param requested 요청한 슬롯 수 (1~64)
	 * @return Mono<String> txId (나중에 롤백/커밋에 사용)
	 * @throws PaintRateLimitException 슬롯 부족 시
	 */
	public Mono<String> beginConsume(UUID actor, int requested) {
		final String slot_key = "rate_limit:slots:" + actor;
		final String txId = genTxId();
		final String rollback_key = "rate_limit:rollback:" + actor + ":" + txId;
		final long now = System.currentTimeMillis();

		return redisTemplate.execute(
				luaConsumeWithTx,
				List.of(slot_key, rollback_key),
				List.of(
					String.valueOf(USER_MAX_TOKENS),
					String.valueOf(requested),
					String.valueOf(now),
					String.valueOf(REFILL_RATE_MS)
				)
			)
			.single() // // Flux<Long> → Mono<Long> (결과 1개만 기대)
			.flatMap(result -> {
				if (result == null || result == -1L) {
					return Mono.error(new PaintRateLimitException(PaintErrorCode.RATE_LIMIT_EXCEEDED));
				}
				log.info("[RATE_LIMIT] beginConsume ok: actor={}, requested={}, txId={}", actor, requested, txId);
				return Mono.just(txId);
			});
	}

	/**
	 * 배치 롤백
	 *
	 * 동작:
	 * 1. rollback_key에서 이번 트랜잭션의 슬롯 ID 목록 조회
	 * 2. slots_key에서 해당 슬롯들만 정확히 삭제
	 * 3. rollback_key 삭제
	 *
	 * @param actor 사용자 ID
	 * @param txId 롤백할 트랜잭션 ID
	 * @return Mono<Void> 완료 신호
	 */
	public Mono<Void> rollbackTx(UUID actor, String txId) {
		final String slot_key = "rate_limit:slots:" + actor;
		final String rollback_key = "rate_limit:rollback:" + actor + ":" + txId;

		return redisTemplate.execute(
				luaRollbackTx,
				List.of(slot_key, rollback_key),
				Collections.emptyList()
			)
			.single()
			.doOnNext(removed -> log.warn("[RATE_LIMIT] rollbackTx: actor={}, txId={}, removed={}", actor, txId, removed))
			.then(); // 값 버리고 완료 신호만 반환
	}

	/**
	 * 배치 커밋
	 *
	 * 동작:
	 * 성공했으므로 추적 명단(rollback_key)만 삭제
	 * - slots_key의 슬롯들은 만료시까지 유지
	 *
	 * @param actor 사용자 ID
	 * @param txId 커밋할 트랜잭션 ID
	 * @return Mono<Void> 완료 신호
	 */
	public Mono<Void> commitTx(UUID actor, String txId) {
		final String rollback_key = "rate_limit:rollback:" + actor + ":" + txId;
		return redisTemplate.execute(
				luaCommitTx,
				Collections.singletonList(rollback_key),
				Collections.emptyList()
			)
			.single()
			.doOnNext(r -> log.info("[RATE_LIMIT] commitTx: actor={}, txId={} (txSet deleted)", actor, txId))
			.then();
	}

	/**
	 * 트랜잭션 ID 생성
	 *
	 * 형식: {시간(16진수)}-{난수(16진수)}
	 * 예: "18bc4a2f3e8-7a3f9c21"
	 *
	 * 목적: 동시에 실행되는 배치 요청들을 구분
	 */
	private static String genTxId() {
		return Long.toHexString(System.currentTimeMillis()) + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt()); // 난수 생성 - 충돌 방지
	}
}
