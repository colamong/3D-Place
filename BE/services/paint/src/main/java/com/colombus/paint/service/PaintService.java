package com.colombus.paint.service;

import java.time.Instant;
import java.util.UUID;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.PaintResponse;
import com.colombus.common.domain.dto.TombstoneDTO;
import com.colombus.common.kafka.paint.event.PaintEvent;
import com.colombus.common.kafka.paint.model.PaintColorSchema;
import com.colombus.common.kafka.paint.model.PaintOperationType;
import com.colombus.paint.contract.dto.EraseRequest;
import com.colombus.paint.contract.dto.PaintRequest;
import com.colombus.paint.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaintService {

	private static final Logger log = LoggerFactory.getLogger(PaintService.class);
	private static final String KAFKA_TOPIC_PAINT = "colombus-paint-events";

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final ReactiveStringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final PaintRetryService paintRetryService;

	public PaintService(
		KafkaTemplate<String, Object> kafkaTemplate,
		ReactiveStringRedisTemplate redisTemplate,
		ObjectMapper objectMapper,
		@Lazy PaintRetryService paintRetryService
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.paintRetryService = paintRetryService;
	}

	/**
	 * 검증된 Paint 요청 처리
	 */
	public Mono<Void> processValidatedPaintRequest(FilterService.ValidatedPaintRequest validated) {
		if (validated.isOverwrite()) {
			// 덮어쓰기: 기존 블록은 tombstone, 새 블록은 delta
			return processOverwrite(validated);
		} else {
			// 신규: delta만 생성
			return processNewPaint(validated);
		}
	}

	/**
	 * 검증된 Erase 요청 처리
	 */
	public Mono<Void> processValidatedEraseRequest(FilterService.ValidatedEraseRequest validated) {
		EraseRequest request = validated.request();
		UUID actor = validated.actor();

		TombstoneDTO tombstone = TombstoneDTO.builder()
			.opId(request.existingOpId())  // 기존 블록의 opId
			.voxelId(validated.voxelId())
			.vSeq(request.vSeq())
			.actor(actor)
			.timestamp(request.timestamp())
			.build();

		PaintResponse response = PaintResponse.fromTombstone(
			tombstone,
			validated.voxelIndex(),
			validated.chunkIndex(),
			request.vSeq()
		);

		return processTombstoneWithRetry(tombstone, validated.chunkId(), response)
			.doOnSuccess(v -> log.info("Erase 처리 완료: voxelId={}", validated.voxelId()));
	}

	/**
	 * 신규 Paint 처리
	 */
	private Mono<Void> processNewPaint(FilterService.ValidatedPaintRequest validated) {
		PaintRequest request = validated.request();
		UUID actor = validated.actor();
		String chunkId = validated.chunkId();
		int voxelId = validated.voxelId();

		DeltaDTO delta = createDelta(request, voxelId, actor);

		PaintResponse response = PaintResponse.fromDelta(
			delta, validated.voxelIndex(), validated.chunkIndex()
		);

		log.info("신규 Paint 처리: opId={}, voxelId={}", delta.opId(), voxelId);

		return processDeltaWithRetry(delta, chunkId, response)
			.doOnSuccess(v -> log.info("신규 Paint 완료: voxelId={}", voxelId));
	}

	/**
	 * 덮어쓰기 Paint 처리
	 * - 기존 블록 Tombstone (Erase)
	 * - 새 블록 Delta (Upsert)
	 */
	private Mono<Void> processOverwrite(FilterService.ValidatedPaintRequest validated) {
		PaintRequest request = validated.request();
		UUID actor = validated.actor();
		String chunkId = validated.chunkId();
		int voxelId = validated.voxelId();

		int currentVSeq = request.vSeq();
		int previousVSeq = currentVSeq - 1;

		// 기존 블록은 tombstone으로 처리
		TombstoneDTO tombstone = TombstoneDTO.builder()
			.opId(request.existingOpId())
			.voxelId(voxelId)
			.vSeq(previousVSeq)
			.actor(actor)
			.timestamp(request.timestamp())
			.build();

		PaintResponse eraseResponse = PaintResponse.fromTombstone(
			tombstone, validated.voxelIndex(), validated.chunkIndex(), previousVSeq
		);

		// 새 블록은 delta로 처리
		DeltaDTO newDelta = createDelta(request, voxelId, actor);
		PaintResponse upsertResponse = PaintResponse.fromDelta(
			newDelta, validated.voxelIndex(), validated.chunkIndex()
		);

		log.info("덮어쓰기 처리: 기존 opId={}, 새 opId={}", request.existingOpId(), newDelta.opId());

		return processTombstoneWithRetry(tombstone, chunkId, eraseResponse) // erase 먼저 하고
			.then(processDeltaWithRetry(newDelta, chunkId, upsertResponse)) // 그 다음에 upsert
			.doOnSuccess(v -> log.info("덮어쓰기 완료: voxelId={}", voxelId));
	}

	/**
	 * Delta 처리 (Redis 저장 → Kafka 전송)
	 * - 각 단계 실패 시 Caffeine 캐시에 저장
	 *
	 * @param delta Delta 데이터
	 * @param chunkId 청크 ID
	 * @param response Kafka 전송용 응답
	 * @return 완료 Mono
	 */
	private Mono<Void> processDeltaWithRetry(
		DeltaDTO delta,
		String chunkId,
		PaintResponse response) {
		return saveDelta(delta, chunkId)
			.onErrorResume(redisError -> {
				// Redis 저장 실패 → Caffeine 캐시에 저장 (Redis + Kafka 재시도)
				log.error("Delta Redis 저장 실패, 재시도 큐에 추가: opId={}", delta.opId(), redisError);
				saveToRetryCache(delta, null, chunkId, response, true, true, redisError);

				return Mono.error(redisError);
			})
			.then(sendToKafka(response)
				.onErrorResume(kafkaError -> {
					// Kafka 전송만 실패 → Caffeine 캐시에 저장 (Kafka만 재시도)
					log.error("Delta Kafka 전송 실패, 재시도 큐에 추가: opId={}", delta.opId(), kafkaError);
					saveToRetryCache(delta, null, chunkId, response, false, true, kafkaError);

					return Mono.error(kafkaError);
				})
			);
	}

	/**
	 * Tombstone 처리 (Redis 저장 → Kafka 전송)
	 * - 각 단계 실패 시 Caffeine 캐시에 저장
	 *
	 * @param tombstone Tombstone 데이터
	 * @param chunkId 청크 ID
	 * @param response Kafka 전송용 응답
	 * @return 완료 Mono
	 */
	private Mono<Void> processTombstoneWithRetry(
		TombstoneDTO tombstone,
		String chunkId,
		PaintResponse response
	) {
		return saveTombstone(tombstone, chunkId)
			.onErrorResume(redisError -> {
				// Redis 저장 실패 → Caffeine 캐시에 저장 (Redis + Kafka 재시도)
				log.error("Tombstone Redis 저장 실패, 재시도 큐에 추가: opId={}", tombstone.opId(), redisError);
				saveToRetryCache(null, tombstone, chunkId, response, true, true, redisError);

				return Mono.error(redisError);
			})
			.then(sendToKafka(response)
				.onErrorResume(kafkaError -> {
					// Kafka 전송만 실패 → Caffeine 캐시에 저장 (Kafka만 재시도)
					log.error("Tombstone Kafka 전송 실패, 재시도 큐에 추가: opId={}", tombstone.opId(), kafkaError);
					saveToRetryCache(null, tombstone, chunkId, response, false, true, kafkaError);

					return Mono.error(kafkaError);
				})
			);
	}

	/**
	 * 실패한 작업을 (재시도 하는) 캐시에 저장
	 *
	 * @param delta Delta 데이터 (Paint일 때)
	 * @param tombstone Tombstone 데이터 (Erase일 때)
	 * @param chunkId 청크 ID
	 * @param response Kafka 전송용 응답
	 * @param needsRedis Redis 재시도 필요 여부
	 * @param needsKafka Kafka 재시도 필요 여부
	 * @param error 에러 정보
	 */
	private void saveToRetryCache(
		DeltaDTO delta,
		TombstoneDTO tombstone,
		String chunkId,
		PaintResponse response,
		boolean needsRedis,
		boolean needsKafka,
		Throwable error
	) {
		// Delta인지 Tombstone인지 판단
		boolean isDelta = (delta != null);
		UUID opId = isDelta ? delta.opId() : tombstone.opId();
		FailedPaintOperation.OperationType type = isDelta
			? FailedPaintOperation.OperationType.DELTA
			: FailedPaintOperation.OperationType.TOMBSTONE;

		// FailedPaintOperation 생성
		FailedPaintOperation failedPaintOperation = FailedPaintOperation.builder()
			.operationId(opId.toString())
			.type(type)
			.delta(delta)
			.tombstone(tombstone)
			.chunkId(chunkId)
			.paintResponse(response)
			.retryCount(0)
			.firstFailedAt(Instant.now())
			.lastRetryAt(Instant.now())
			.failureReason(error.getMessage())
			.needsRedis(needsRedis)
			.needsKafka(needsKafka)
			.build();

		// 캐시에 저장
		paintRetryService.saveFailedOperation(failedPaintOperation);
	}

	/**
	 * DeltaDTO 생성
	 */
	private DeltaDTO createDelta(PaintRequest request, int voxelId, UUID actor) {
		return DeltaDTO.builder()
			.opId(request.opId())  // 요청에서 받은 opId 사용
			.vSeq(request.vSeq())
			.voxelId(voxelId)
			.faceMask(request.faceMask())
			.colorSchema(request.colorSchema())
			.colorBytes(request.colorBytes())
			.actor(actor)
			.policyTags(request.policyTags())
			.timestamp(request.timestamp())
			.build();
	}

	/**
	 * Delta를 Redis에 저장
	 */
	Mono<Void> saveDelta(DeltaDTO delta, String chunkId) {
		String sortedSetKey = "op_ids:" + chunkId;
		String hashKey = "deltas:" + chunkId;
		String stateKey = "voxel_state:" + chunkId;

		String opIdStr = delta.opId().toString();
		double score = delta.timestamp().toEpochMilli();

		Mono<Boolean> addToSortedSet = redisTemplate.opsForZSet()
			.add(sortedSetKey, opIdStr, score)
			.doOnSuccess(added -> log.debug("Redis ZSET 저장: {}", added));

		Mono<Boolean> addToHash = Mono.fromCallable(() -> objectMapper.writeValueAsString(delta))
			.flatMap(json -> redisTemplate.opsForHash().put(hashKey, opIdStr, json))
			.doOnSuccess(added -> log.debug("Redis HASH 저장: {}", added));

		// VoxelStateDTO 생성 (현재 상태)
		VoxelState currentState = VoxelState.builder()
			.opId(delta.opId())
			.vSeq(delta.vSeq())
			.exists(true) // Paint는 존재함
			.build();

		// voxel_state 해시에 현재 상태 저장
		Mono<Boolean> updateStateHash = Mono.fromCallable(() -> objectMapper.writeValueAsString(currentState))
			.flatMap(json -> redisTemplate.opsForHash().put(stateKey, String.valueOf(delta.voxelId()), json))
			.doOnSuccess(added -> log.debug("Redis HASH (voxel_state) 저장: {}", added));

		return Mono.zip(addToSortedSet, addToHash, updateStateHash).then();
	}

	/**
	 * Tombstone을 Redis에 저장
	 */
	Mono<Void> saveTombstone(TombstoneDTO tombstone, String chunkId) {
		String tombKey = "tombstones:" + chunkId;
		String stateKey = "voxel_state:" + chunkId;

		String opIdStr = tombstone.opId().toString();
		double score = tombstone.timestamp().toEpochMilli();

		Mono<Boolean> addToTombSet = redisTemplate.opsForZSet()
			.add(tombKey, opIdStr, score)
			.doOnSuccess(added -> log.debug("Tombstone 저장: {}", added));

		// VoxelStateDTO 생성 (Erase 상태)
		VoxelState erasedState = VoxelState.builder()
			.opId(tombstone.opId())
			.vSeq(tombstone.vSeq()) // Erase 요청의 vSeq를 저장
			.exists(false) // Erase는 존재하지 않음
			.build();

		// voxel_state 해시에 Erase 상태 저장
		Mono<Boolean> updateStateHash = Mono.fromCallable(() -> objectMapper.writeValueAsString(erasedState))
			.flatMap(json -> redisTemplate.opsForHash().put(stateKey, String.valueOf(tombstone.voxelId()), json))
			.doOnSuccess(added -> log.debug("Redis HASH (voxel_state) 저장: {}", added));

		return Mono.zip(addToTombSet, updateStateHash).then();
	}

	/**
	 * Kafka에 PaintEvent 전송
	 */
	Mono<Void> sendToKafka(PaintResponse response) {
		PaintEvent event = new PaintEvent(
			response.opId(),
			response.voxelIndex(),
			response.chunkIndex(),
			response.faceMask(),
			response.vSeq(),
			response.colorSchema() != null ? PaintColorSchema.valueOf(response.colorSchema().name()) : null,
			response.colorBytes(),
			response.actor(),
			response.timestamp(),
			PaintOperationType.valueOf(response.operationType().name())
		);

		String key = response.opId().toString();

		return Mono.fromFuture(
			kafkaTemplate.send(KAFKA_TOPIC_PAINT, key, event)
				.toCompletableFuture()
			)
			.doOnSuccess(result -> log.debug("Kafka 전송 성공: type={}", response.operationType()))
			.doOnError(ex -> log.error("Kafka 전송 실패", ex))
			.then();
	}
}