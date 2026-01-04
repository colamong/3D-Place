package com.colombus.paint.service;

import com.colombus.common.domain.dto.ChunkIndexDTO;
import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.VoxelIndexDTO;
import com.colombus.common.web.core.exception.BusinessException;
import com.colombus.paint.contract.dto.EraseRequest;
import com.colombus.paint.contract.dto.PaintRequest;
import com.colombus.paint.dto.*;
import com.colombus.paint.exception.PaintErrorCode;
import com.colombus.paint.exception.PaintForbiddenException;
import com.colombus.paint.exception.PaintInputException;
import com.colombus.paint.exception.PaintNotFoundException;
import com.colombus.paint.exception.RedisOperationException;
import com.colombus.paint.util.PaintUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 요청 검증 및 필터링 담당
 * - vSeq 검증
 * - 신규/덮어쓰기 판단
 * - Delta/Tombstone 분류
 * - S3 데이터 Redis 캐싱
 */
@Service
public class FilterService {

	private static final Logger log = LoggerFactory.getLogger(FilterService.class);

	private final ReactiveStringRedisTemplate redisTemplate;
	private final S3Service s3Service;
	private final ObjectMapper objectMapper;

	public FilterService(
		ReactiveStringRedisTemplate redisTemplate,
		S3Service s3Service,
		ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplate;
		this.s3Service = s3Service;
		this.objectMapper = objectMapper;
	}

	/**
	 * Paint 요청 검증 및 처리
	 * - vSeq 검증 후, exist 여부에 따라 new/overwrite 결정
	 */
	public Mono<ValidatedPaintRequest> validatePaintRequest(PaintRequest request, UUID actor) {

		VoxelIndexDTO voxelIndexDTO = request.voxelIndex();
		int voxelId = PaintUtil.packXYZ(voxelIndexDTO.vix(), voxelIndexDTO.viy(), voxelIndexDTO.viz());

		ChunkIndexDTO chunkIndexDTO = request.chunkIndex();
		String chunkId = PaintUtil.createChunkId(chunkIndexDTO);

		return getAndValidateVoxelState(chunkId, voxelId, request.vSeq())
			.flatMap(currentState -> {
				// 이미 존재하는 블록 위에 칠하기(덮어쓰기)
				if (currentState.exists()) {
					if (request.existingOpId() == null) {
						log.info("덮어쓰기(exists=true) 요청이나 existingOpId 누락. voxelId: " + voxelId);
						return Mono.error(new PaintInputException(PaintErrorCode.MISSING_EXISTING_OP_ID));
					}
					return Mono.just(ValidatedPaintRequest.overwrite(request, voxelId, chunkId, voxelIndexDTO, chunkIndexDTO, actor));
				}
				// 빈 공간에 칠하기(새로쓰기 or 삭제된 후 다시 칠하기)
				else {
					// 빈 공간이므로 existingOpId는 필요 없음
					return Mono.just(ValidatedPaintRequest.newPaint(request, voxelId, chunkId, voxelIndexDTO, chunkIndexDTO, actor));
				}
			});
	}

	/**
	 * Erase 요청 검증 및 처리
	 */
	public Mono<ValidatedEraseRequest> validateEraseRequest(EraseRequest request, UUID actor) {

		VoxelIndexDTO voxelIndexDTO = request.voxelIndex();
		int voxelId = PaintUtil.packXYZ(voxelIndexDTO.vix(), voxelIndexDTO.viy(), voxelIndexDTO.viz());

		ChunkIndexDTO chunkIndexDTO = request.chunkIndex();
		String chunkId = PaintUtil.createChunkId(chunkIndexDTO);

		return getAndValidateVoxelState(chunkId, voxelId, request.vSeq())
			.flatMap(currentState -> {
				// Erase는 대상 필요
				if (!currentState.exists()) {
					log.info("삭제할 voxel이 존재하지 않습니다.");
					return Mono.error(new PaintNotFoundException(PaintErrorCode.VOXEL_NOT_FOUND));
				}
				// existingOpId가 없으면 삭제할 블록이 없음
				if (request.existingOpId() == null) {
					log.info("삭제할 voxel이 존재하지 않습니다.");
					return Mono.error(new PaintNotFoundException(PaintErrorCode.VOXEL_NOT_FOUND));
				}
				// 권한 확인 - 유저
				return getOriginalDelta(chunkId, request.existingOpId().toString())
					.flatMap(originalDelta -> {
						// 기존 Delta의 주인(originalDelta.actor)과 새로 요청하는 사람(actor)이 일치하는지 확인
						if (originalDelta.actor().equals(actor)) {
							log.debug("소유권 일치, 삭제 승인: Actor={}", actor);
							return Mono.just(new ValidatedEraseRequest(request, voxelId, chunkId, voxelIndexDTO, chunkIndexDTO, actor));
						} else {
							// 소유권 불일치
							log.warn("픽셀 삭제 권한 없음 (소유자 불일치): Actor={}, Owner={}", actor, originalDelta.actor());
							return Mono.error(new PaintForbiddenException(PaintErrorCode.VOXEL_PERMISSION_DENIED));
						}
					});
			})
			.onErrorResume(NoSuchElementException.class, e ->
				Mono.error(new PaintNotFoundException(PaintErrorCode.VOXEL_NOT_FOUND))
			);
	}

	/**
	 * vSeq 검증 및 상태반환
	 * - Redis에서 현재 vSeq 조회
	 * - 없으면 S3에서 조회 후 Redis에 캐싱
	 * - Redis/S3의 vSeq + 1 == 요청 vSeq 이면 통과
	 * - 삭제된 상태(exists=false)여도 vSeq가 연속되면 통과
	 */
	private Mono<VoxelState> getAndValidateVoxelState(String chunkId, int voxelId, int requestVSeq) {

		return getVoxelStateFromRedis(chunkId, voxelId)
			.switchIfEmpty(Mono.defer(() -> {
				// Redis에 없으면 S3에서 조회
				log.debug("Redis에 voxel 정보 없음, S3 조회: voxelId={}", voxelId);
				return s3Service.getVoxelState(chunkId, voxelId)
					.flatMap(s3State -> {
						if (s3State.exists()) {
							// S3에서 찾은 데이터를 Redis에 캐싱
							log.info("S3에서 voxel 발견, Redis에 캐싱: opId={}, vSeq={}",
								s3State.opId(), s3State.vSeq());
							return cacheVoxelToRedis(s3State, chunkId, voxelId)
								.thenReturn(s3State);
						}
						// S3에도 없으면 그냥 반환
						return Mono.just(s3State);
					});
			}))
			// VoxelStateDTO를 성공적으로 가져왔을 때 - vSeq 비교 시작
			.flatMap(state -> {
				boolean isValid;

				if (!state.exists()) {
					// 존재하지 않는 경우(처음 or 지워진경우)
					// 현재 vSeq가 0(처음)이면 1 기대
					// 현재 vSeq가 n(지워짐)이면 n+1 기대
					int expectedSeq = (state.vSeq() == 0) ? 1 : state.vSeq() + 1;
					isValid = (requestVSeq == expectedSeq);
				} else {
					// 존재하는 경우: 현재 n이면 n+1 기대
					isValid = (state.vSeq() + 1 == requestVSeq);
				}

				if (!isValid) {
					log.info("vSeq 불일치: currentVSeq={}, requestVSeq={}, exists={}",
						state.vSeq(), requestVSeq, state.exists());
					return Mono.error(new PaintInputException(PaintErrorCode.INVALID_VSEQ));
				}

				// 검증 통과 시 상태 객체 반환
				return Mono.just(state);
			});
	}

	private Mono<VoxelState> getVoxelStateFromRedis(String chunkId, int voxelId) {
		String stateKey = "voxel_state:" + chunkId;
		String field = String.valueOf(voxelId);

		return redisTemplate.opsForHash().get(stateKey, field)
			.flatMap(json -> {
				// Redis에 상태(JSON)가 있으면
				try {
					VoxelState state = objectMapper.readValue((String) json, VoxelState.class);
					log.debug("Redis에서 voxel_state 발견: vSeq={}, exists={}", state.vSeq(), state.exists());
					return Mono.just(state); // vSeq 비교
				} catch (JsonProcessingException e) {
					log.error("voxel_state 파싱 실패", e);
					// Mono.empty - 500 에러로 수정
					return Mono.error(new RedisOperationException(PaintErrorCode.DELTA_PARSE_FAILED));
				}
			})
			// 에러가 BusinessException의 계열이면 조건없이 그대로 전파
			.onErrorResume(BusinessException.class, Mono::error)
			// 그 외 모든 에러만 래핑해서 중복 래핑 방지
			.onErrorResume(e -> {
				log.error("Redis 'voxel_state' 조회 실패: chunkId={}, voxelId={}", chunkId, voxelId, e);
				return Mono.error(new RedisOperationException(PaintErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));
			})
			// redisTemplate.opsForHash().get(stateKey, field)의 값이 존재하지 않을 때
			.switchIfEmpty(Mono.defer(() -> {
				log.debug("Redis에 voxel_state 없음: voxelId={}", voxelId);
				return Mono.empty(); // S3 조회 로직으로 넘어감
			}));
	}

	/**
	 * S3에서 조회한 voxel 데이터를 Redis에 캐싱
	 * - vSeq 검증에 필요한 VoxelStateDTO만 Redis의 voxel_state hash에 저장
	 */
	private Mono<Void> cacheVoxelToRedis(VoxelState s3State, String chunkId, int voxelId) {
		if (!s3State.exists()) {
			return Mono.empty(); // 종료
		}

		// voxel_state 캐싱 키 및 필드 준비
		String stateKey = "voxel_state:" + chunkId;
		String field = String.valueOf(voxelId);

		// S3에서 가져온 VoxelStateDTO를 JSON으로 변환
		Mono<String> jsonMono = Mono.fromCallable(() -> objectMapper.writeValueAsString(s3State));

		// voxel_state 해시에 상태 요약본을 저장하는 작업만 수행
		Mono<Boolean> cacheState = jsonMono
			.flatMap(json -> redisTemplate.opsForHash().put(stateKey, field, json))
			.doOnSuccess(added -> log.debug("S3→Redis HASH (voxel_state) 캐싱 완료: {}", added));

		// cacheState 작업만 실행하고 완료
		return cacheState
			.then()
			.onErrorResume(e -> {
				log.error("S3 VoxelState Redis 캐싱 실패: chunkId={}, voxelId={}", chunkId, voxelId, e);
				return Mono.empty(); // 캐싱 실패해도 vSeq 검증은 계속 진행
			});
	}

	/**
	 * Redis 및 S3에서 DeltaDTO 가져오기
	 */
	private Mono<DeltaDTO> getOriginalDelta(String chunkId, String opIdStr) {
		String hashKey = "deltas:" + chunkId;

		UUID opId;
		try {
			opId = UUID.fromString(opIdStr);
		} catch (IllegalArgumentException e) {
			log.warn("권한 검증 실패: 잘못된 opId 형식입니다. OpId={}", opIdStr);
			return Mono.error(new PaintInputException(PaintErrorCode.INVALID_OPID_FORMAT));
		}

		// Redis에서 deltas: 먼저 조회
		return redisTemplate.opsForHash().get(hashKey, opIdStr)
			.flatMap(json -> {
				try {
					// Redis에 저장된 JSON을 DeltaDTO로 변환
					DeltaDTO delta = objectMapper.readValue((String) json, DeltaDTO.class);
					log.debug("Redis의 deltas에서 발견한 opId: {}", opIdStr);
					return Mono.just(delta);
				} catch (Exception e) {
					log.error("Redis DeltaDTO 파싱 실패: opId={}", opIdStr, e);
					return Mono.error(new RedisOperationException(PaintErrorCode.DELTA_PARSE_FAILED));
				}
			})
			.onErrorResume(BusinessException.class, Mono::error)
			.onErrorResume(e -> {
				log.error("Redis 'deltas' 조회 실패: chunkId={}, opId={}", chunkId, opIdStr, e);
				return Mono.error(new RedisOperationException(PaintErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));
			})
			// Redis에 없는 경우, S3 조회
			.switchIfEmpty(Mono.defer(() -> {
				log.warn("Redis의 deltas에 데이터 없음, S3에서 조회: opId={}", opIdStr);
				return s3Service.getDeltaByOpId(chunkId, opId)
					.switchIfEmpty(Mono.defer(() -> {
						log.error("S3에도 해당 opId 없음: opId={}", opIdStr);
						return Mono.error(new PaintNotFoundException(PaintErrorCode.VOXEL_NOT_FOUND));					}));
			}));
	}

	/**
	 * 검증된 Paint 요청(Wrapper)
	 */
	public record ValidatedPaintRequest(
		PaintRequest request,
		int voxelId, 	// redis 저장용
		String chunkId, // redis 저장용
		VoxelIndexDTO voxelIndex, // socket 전송용
		ChunkIndexDTO chunkIndex, // socket 전송용
		boolean isOverwrite, // 새로쓰기, 덮어쓰기 분류
		UUID actor // actor는 UUID 타입의 userId
	) {
		public static ValidatedPaintRequest newPaint(
			PaintRequest request, int voxelId, String chunkId, VoxelIndexDTO voxelIndex, ChunkIndexDTO chunkIndex, UUID actor
		) {
			return new ValidatedPaintRequest(request, voxelId, chunkId, voxelIndex, chunkIndex, false, actor);
		}

		public static ValidatedPaintRequest overwrite(
			PaintRequest request, int voxelId, String chunkId, VoxelIndexDTO voxelIndex, ChunkIndexDTO chunkIndex, UUID actor
		) {
			return new ValidatedPaintRequest(request, voxelId, chunkId, voxelIndex, chunkIndex, true, actor);
		}
	}

	/**
	 * 검증된 Erase 요청(Wrapper)
	 */
	public record ValidatedEraseRequest(
		EraseRequest request,
		int voxelId,
		String chunkId,
		VoxelIndexDTO voxelIndex,
		ChunkIndexDTO chunkIndex,
		UUID actor
	) {
	}
}