package com.colombus.paint.service;

import com.colombus.paint.dto.FailedPaintOperation;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Paint/Erase 작업 재시도
 * - 실패한 작업을 Caffeine 캐시에 저장
 * - 5초마다 자동으로 재시도
 * - 최대 3번까지 재시도
 */
@Service
public class PaintRetryService {

	private static final Logger log = LoggerFactory.getLogger(PaintRetryService.class);

	private static final int MAX_RETRY_COUNT = 3;                             // 최대 3번 재시도
	private static final Duration MIN_RETRY_INTERVAL = Duration.ofSeconds(5); // 최소 5초 간격(부하 방지)

	private final Cache<String, FailedPaintOperation> failedPaintOperationCache;
	private final PaintService paintService;

	public PaintRetryService(
		Cache<String, FailedPaintOperation> failedPaintOperationCache,
		PaintService paintService
	) {
		this.failedPaintOperationCache = failedPaintOperationCache;
		this.paintService = paintService;
	}

	/**
	 * 실패한 작업을 캐시에 저장
	 * - PaintService에서 Redis/Kafka 실패 시 호출
	 *
	 * @param operation 실패한 작업 정보
	 */
	public void saveFailedOperation(FailedPaintOperation operation) {
		failedPaintOperationCache.put(operation.operationId(), operation);
		log.warn("실패한 작업 캐시 저장: opId={}, type={}, needsRedis={}, needsKafka={}, reason={}",
			operation.operationId(),
			operation.type(),
			operation.needsRedis(),
			operation.needsKafka(),
			operation.failureReason());
	}

	/**
	 * 5초마다 실패한 작업들을 재시도
	 * - @Scheduled로 자동 실행
	 * - 캐시에 있는 실패한 모든 작업 조회
	 * - 재시도 가능한 것만 재시도
	 */
	@Scheduled(fixedDelay = 5000)
	public void retryFailedOperations() {
		// 캐시에서 실패한 모든 작업 가져오기
		var failedOps = new ArrayList<>(failedPaintOperationCache.asMap().values());

		// 실패한 작업이 없을 때
		if (failedOps.isEmpty()) {
			return;
		}

		log.info("재시도 배치 시작: 총 {}개 작업", failedOps.size());

		// 각 작업별로 재시도
		failedOps.forEach(op -> {
			if (shouldRetry(op)) {
				retryOperation(op);
			}
		});
	}

	/**
	 * 재시도 가능 여부 확인
	 * - 최대 재시도 횟수 초과 시 false
	 *
	 * @param op 확인할 작업
	 * @return true: 재시도 가능, false: 재시도 불가
	 */
	private boolean shouldRetry(FailedPaintOperation op) {
		// 최대 재시도 횟수를 초과 했는지
		if (op.retryCount() >= MAX_RETRY_COUNT) {
			log.error("최대 재시도 횟수 초과, 캐시에서 제거: opId={}, retryCount={}",
				op.operationId(), op.retryCount());

			failedPaintOperationCache.invalidate(op.operationId());

			return false;
		}

		// 최소 재시도 간격 확인 (마지막 재시도로부터 5초 이상 경과했는지)
		Duration timeSinceLastRetry = Duration.between(op.lastRetryAt(), Instant.now());
		if (timeSinceLastRetry.compareTo(MIN_RETRY_INTERVAL) < 0) {
			log.debug("재시도 간격 미달, 스킵: opId={}, 경과시간={}초",
				op.operationId(), timeSinceLastRetry.getSeconds());
			return false;
		}

		return true;
	}

	/**
	 * 개별 작업 재시도 실행
	 * - type에 따라 Delta 또는 Tombstone 재시도
	 * - 성공 시 캐시에서 제거
	 * - 실패 시 retryCount 증가 후 다시 캐시에 저장
	 *
	 * @param op 재시도할 작업
	 */
	private void retryOperation(FailedPaintOperation op) {
		log.info("작업 재시도 중: opId={}, type={}, retryCount={}, needsRedis={}, needsKafka={}",
			op.operationId(),
			op.type(),
			op.retryCount() + 1,
			op.needsRedis(),
			op.needsKafka());

		// type에 따라 재시도 분기 처리
		Mono<Void> retryMono = switch (op.type()) {
			case DELTA -> retryDelta(op);
			case TOMBSTONE -> retryTombstone(op);
		};

		// 재시도 실행
		retryMono // 계획
			.doOnSuccess(v -> {
				// 재시도 성공 → 캐시에서 제거
				log.info("재시도 성공, 캐시에서 제거: opId={}", op.operationId());
				failedPaintOperationCache.invalidate(op.operationId());
			})
			.doOnError(e -> {
				// 재시도 실패 → retryCount 증가 후 다시 캐시에 저장
				log.warn("재시도 실패: opId={}, error={}", op.operationId(), e.getMessage());
				failedPaintOperationCache.put(op.operationId(), op.withRetry()); // key(op.operationId())에 덮어쓰기
			})
			.subscribe(); // 비동기 실행 → 바로 실행 명령
	}

	/**
	 * Delta 작업 재시도
	 * - needsRedis가 true면 Redis 저장
	 * - needsKafka가 true면 Kafka 전송
	 *
	 * @param op Delta 작업 정보
	 * @return 재시도 Mono
	 */
	private Mono<Void> retryDelta(FailedPaintOperation op) {
		// Redis 재시도
		Mono<Void> redisMono = op.needsRedis()
			? paintService.saveDelta(op.delta(), op.chunkId())
			.doOnSuccess(v -> log.info("Delta Redis 재시도 성공: opId={}", op.operationId()))
			: Mono.empty(); // needsRedis = false면 건너뛰기

		// Kafka 재시도
		Mono<Void> kafkaMono = op.needsKafka()
			? paintService.sendToKafka(op.paintResponse())
			.doOnSuccess(v -> log.info("Delta Kafka 재시도 성공: opId={}", op.operationId()))
			: Mono.empty(); // needsKafka = false면 건너뛰기

		// Redis → Kafka 순서로 실행
		return redisMono.then(kafkaMono);
	}

	/**
	 * Tombstone 작업 재시도
	 * - needsRedis가 true면 Redis 저장
	 * - needsKafka가 true면 Kafka 전송
	 *
	 * @param op Tombstone 작업 정보
	 * @return 재시도 Mono
	 */
	private Mono<Void> retryTombstone(FailedPaintOperation op) {
		// Redis 재시도
		Mono<Void> redisMono = op.needsRedis()
			? paintService.saveTombstone(op.tombstone(), op.chunkId())
			.doOnSuccess(v -> log.info("Tombstone Redis 재시도 성공: opId={}", op.operationId()))
			: Mono.empty();

		// Kafka 재시도
		Mono<Void> kafkaMono = op.needsKafka()
			? paintService.sendToKafka(op.paintResponse())
			.doOnSuccess(v -> log.info("Tombstone Kafka 재시도 성공: opId={}", op.operationId()))
			: Mono.empty();

		// Redis → Kafka 순서로 실행
		return redisMono.then(kafkaMono);
	}
}