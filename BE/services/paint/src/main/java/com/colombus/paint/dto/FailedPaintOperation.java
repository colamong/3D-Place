package com.colombus.paint.dto;

import java.time.Instant;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.PaintResponse;
import com.colombus.common.domain.dto.TombstoneDTO;

/**
 * 실패한 Paint/Erase 정보
 * - Redis 저장 및 Kafka 전송 실패 시 생성
 * - Caffeine에 저장되어 재시도
 * */
public record FailedPaintOperation(
	String operationId,
	OperationType type,              // DELTA, TOMBSTONE

	// 재시도 데이터
	DeltaDTO delta,                  // Paint 작업일 때 사용 (type=DELTA)
	TombstoneDTO tombstone,          // Erase 작업일 때 사용 (type=TOMBSTONE)
	String chunkId,
	PaintResponse paintResponse,     // Kafka 전송용 응답 객체

	// 재시도 메타정보
	int retryCount,                  // 현재까지 재시도한 횟수
	Instant firstFailedAt,           // 최초 실패 시각
	Instant lastRetryAt,             // 마지막 재시도 시각
	String failureReason,            // 실패 사유

	// 재시도 플래그
	boolean needsRedis,              // true: Redis 저장 재시도
	boolean needsKafka               // true: Kafka 전송 재시도
) {
	public static Builder builder() { return new Builder(); }

	public static class Builder {
		private String operationId;
		private OperationType type;
		private DeltaDTO delta;
		private TombstoneDTO tombstone;
		private String chunkId;
		private PaintResponse paintResponse;
		private int retryCount;
		private Instant firstFailedAt;
		private Instant lastRetryAt;
		private String failureReason;
		private boolean needsRedis;
		private boolean needsKafka;

		public Builder operationId(String operationId) { this.operationId = operationId; return this; }
		public Builder type(OperationType type) { this.type = type; return this; }
		public Builder delta(DeltaDTO delta) { this.delta = delta; return this; }
		public Builder tombstone(TombstoneDTO tombstone) { this.tombstone = tombstone; return this; }
		public Builder chunkId(String chunkId) { this.chunkId = chunkId; return this; }
		public Builder paintResponse(PaintResponse paintResponse) { this.paintResponse = paintResponse; return this; }
		public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
		public Builder firstFailedAt(Instant firstFailedAt) { this.firstFailedAt = firstFailedAt; return this; }
		public Builder lastRetryAt(Instant lastRetryAt) { this.lastRetryAt = lastRetryAt; return this; }
		public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
		public Builder needsRedis(boolean needsRedis) { this.needsRedis = needsRedis; return this; }
		public Builder needsKafka(boolean needsKafka) { this.needsKafka = needsKafka; return this; }

		public FailedPaintOperation build() { return new FailedPaintOperation(operationId, type, delta, tombstone, chunkId,
			paintResponse, retryCount, firstFailedAt, lastRetryAt, failureReason, needsRedis, needsKafka); }
		}

	/**
	 * 작업 타입
	 * - DELTA: Paint (신규/덮어쓰기)
	 * - TOMBSTONE: Erase
	 */
	public enum OperationType {
		DELTA,
		TOMBSTONE
	}

	/**
	 * 재시도 횟수를 증가시킨 새 객체 생성
	 * - 재시도 실패 시 호출
	 *
	 * @return retryCount가 1 증가된 새 FailedPaintOperation
	 */
	public FailedPaintOperation withRetry() {
		return new FailedPaintOperation(
			operationId,
			type,
			delta,
			tombstone,
			chunkId,
			paintResponse,
			retryCount + 1,          // 재시도 횟수 증가
			firstFailedAt,           		  // 최초 실패 시각 유지
			Instant.now(),           		  // 마지막 재시도 시각 갱신
			failureReason,
			needsRedis,
			needsKafka
		);
	}
}