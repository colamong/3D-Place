package com.colombus.paint.controller;

import com.colombus.paint.contract.dto.EraseRequest;
import com.colombus.paint.contract.dto.PaintRequest;
import com.colombus.paint.exception.PaintInputException;
import com.colombus.paint.exception.PaintNotFoundException;
import com.colombus.paint.exception.PaintRateLimitException;
import com.colombus.paint.service.FilterService;
import com.colombus.paint.service.PaintService;
import com.colombus.paint.service.RateLimitService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Paint 요청 처리 엔드포인트
 * - RateLimitService.beginConsume() 으로 슬롯 선점 후
 *   → 모든 요청 검증 + 처리
 *   → 성공 시 commitTx, 실패 시 rollbackTx
 */
@RestController
@RequestMapping("/internal/paints")
public class PaintController {

	private static final Logger log = LoggerFactory.getLogger(PaintController.class);

	private final FilterService filterService;
	private final PaintService paintService;
	private final RateLimitService rateLimitService;

	private static final int USER_MAX_TOKENS = 64; // 한 번에 최대로 보낼 수 있는 양

	public PaintController(
		FilterService filterService,
		PaintService paintService,
		RateLimitService rateLimitService) {
		this.filterService = filterService;
		this.paintService = paintService;
		this.rateLimitService = rateLimitService;
	}

	@PostMapping("/")
	public Mono<ResponseEntity<String>> handlePaint(
		@RequestHeader("X-Internal-Actor-Id") UUID actorId,
		@RequestBody List<PaintRequest> requests
	) {
		UUID actor;
		try {
			actor = actorId;
		} catch (IllegalArgumentException e) {
			// UUID 형식이 아니면 400 반환
			return Mono.just(ResponseEntity.badRequest().body("잘못된 Actor UUID"));
		}

		if (requests == null || requests.isEmpty()) {
			return Mono.just(ResponseEntity.badRequest().body("요청이 비어있습니다."));
		}

		// 한 번의 요청에 들어있는 paint의 개수
		final int requestedSlots = requests.size();

		// 배치 크기 제한
		if (requestedSlots > USER_MAX_TOKENS) {
			return Mono.just(ResponseEntity.badRequest()
				.body("요청당 최대 64개까지 처리할 수 있습니다."));
		}

		log.info("Paint 요청 수신 (Actor: {}): 총 {}건", actor, requestedSlots);

		final String[] txHolder = new String[1]; // txId를 담아두기 위한 1칸짜리 배열

		// RateLimit 슬롯 선점 → 검증 → 처리 → 커밋 / 롤백
		return rateLimitService.beginConsume(actor, requestedSlots) // 슬롯 선점 시도
			.doOnNext(txId -> txHolder[0] = txId) // 성공 시 txId를 txHolder[0]에 저장 - 커밋/롤백에서 재사용
			.thenMany(
				Flux.fromIterable(requests) // List<PaintRequest> → Flux<PaintRequest>
					.flatMap(request -> filterService.validatePaintRequest(request, actor)) // 각 요청마다 vSeq 등 검증 (Mono → flatMap)
					.collectList() // 전체 검증이 끝날 때까지 모아서 List<ValidatedPaintRequest>로 만들기
					.flatMapMany(validated -> {
						// 모든 요청이 검증을 통과한 시점
						log.info("모든 Paint 요청({}) 검증 통과. 처리 시작.", validated.size());
						return Flux.fromIterable(validated)
							.flatMap(paintService::processValidatedPaintRequest); // 각 요청을 실제로 redis/kafka에 반영
					})
			)
			// 위 Flux가 에러없이 모두 끝났다면 → commitTx(rollback_key 삭제)
			.then(rateLimitService.commitTx(actor, txHolder[0]))
			.then(Mono.just(ResponseEntity.accepted().body("Paint 이벤트가 접수되었습니다.")))
			.onErrorResume(PaintRateLimitException.class, e -> {
				log.warn("Paint RateLimit 초과 (Actor: {}): {}", actor, e.getMessage());
				return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage()));
			})
			.onErrorResume(e -> {
				log.error("Paint 배치 실패 → 롤백 실행 (Actor: {}): {}", actor, e.getMessage());
				return rateLimitService.rollbackTx(actor, txHolder[0])
					.then(Mono.just(ResponseEntity.badRequest()
						.body("Paint 요청 실패로 전체 롤백됨: " + e.getMessage())));
			});
	}

	/**
	 * Erase 요청 처리 엔드포인트
	 * - Paint와 동일한 배치 트랜잭션 구조 (beginConsume → 검증 → 처리 → commit / rollback)
	 * - 없는 voxel을 지우려고 하면 NoSuchElementException으로 전체 롤백
	 */
	@DeleteMapping("/erase")
	public Mono<ResponseEntity<String>> handleErase(
		@RequestHeader("X-Internal-Actor-Id") UUID actorId,
		@RequestBody List<EraseRequest> requests
	) {
		UUID actor;
		try {
			actor = actorId;
		} catch (IllegalArgumentException e) {
			// UUID 형식이 아니면 400 반환
			return Mono.just(ResponseEntity.badRequest().body("잘못된 Actor UUID"));
		}

		if (requests == null || requests.isEmpty()) {
			return Mono.just(ResponseEntity.badRequest().body("요청이 비어있습니다."));
		}

		final int requestedSlots = requests.size();
		if (requestedSlots > 64) {
			return Mono.just(ResponseEntity.badRequest()
				.body("요청당 최대 64개까지 처리할 수 있습니다."));
		}

		log.info("Erase 요청 수신 (Actor: {}): 총 {}건", actor, requestedSlots);

		final String[] txHolder = new String[1];

		return rateLimitService.beginConsume(actor, requestedSlots)
			.doOnNext(txId -> txHolder[0] = txId)
			.thenMany(
				Flux.fromIterable(requests)
					// Erase는 순서 중요 - concatMap으로 순차처리
					.concatMap(req -> filterService.validateEraseRequest(req, actor))
					.collectList()
					.flatMapMany(validated -> {
						log.info("모든 Erase 요청({}) 검증 통과. 처리 시작.", validated.size());
						return Flux.fromIterable(validated)
							.concatMap(paintService::processValidatedEraseRequest);
					})
			)
			.then(rateLimitService.commitTx(actor, txHolder[0]))
			.then(Mono.just(ResponseEntity.accepted().body("Erase 요청이 정상 처리되었습니다.")))
			.onErrorResume(PaintRateLimitException.class, e -> {
				log.warn("Paint RateLimit 초과 (Actor: {}): {}", actor, e.getMessage());
				return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage()));
			})
			.onErrorResume(PaintNotFoundException.class, e -> {
				log.warn("삭제할 voxel 없음 (Actor: {}): {}", actor, e.getMessage());
				return rateLimitService.rollbackTx(actor, txHolder[0])
					.then(Mono.just(ResponseEntity.badRequest()
						.body("삭제할 voxel이 존재하지 않아 전체 롤백됨: " + e.getMessage())));
			})
			.onErrorResume(PaintInputException.class, e -> {
				log.warn("유효하지 않은 Erase 요청 (Actor: {}): {}", actor, e.getMessage());
				return rateLimitService.rollbackTx(actor, txHolder[0])
					.then(Mono.just(ResponseEntity.badRequest()
						.body("유효하지 않은 요청 포함으로 전체 롤백됨: " + e.getMessage())));
			})
			.onErrorResume(e -> {
				log.error("Erase 처리 중 알 수 없는 오류 (Actor: {}): {}", actor, e.getMessage());
				return rateLimitService.rollbackTx(actor, txHolder[0])
					.then(Mono.just(ResponseEntity.internalServerError()
						.body("Erase 처리 중 오류 발생, 전체 롤백됨: " + e.getMessage())));
			});
	}
}