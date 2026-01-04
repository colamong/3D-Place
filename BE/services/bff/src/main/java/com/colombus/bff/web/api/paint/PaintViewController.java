package com.colombus.bff.web.api.paint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.colombus.bff.util.ActorIdResolver;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.paint.contract.dto.EraseRequest;
import com.colombus.paint.contract.dto.PaintRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paints")
public class PaintViewController {

	private final WebClient paintClient;
	private final TraceIdProvider trace;

	@PostMapping("/")
	public Mono<ResponseEntity<CommonResponse<Void>>> handlePaint(
		@AuthenticationPrincipal Jwt jwt,
		ServerHttpRequest req,
		@RequestBody List<PaintRequest> body
	) {
		// jwt에서 actor ID 추출 -> paintController에 actorStr로 넘겨서 UUID로 변환
		UUID actorId = ActorIdResolver.requireUserId(jwt);
		// String role  = jwt.getClaimAsString("role");

		// String actorStr = (jwt != null) ? jwt.getSubject() : "00129898-0000-0000-0000-000000000000"; // test
		// String role = "USER"; // test

		Mono<Void> action = paintClient.post()
			.uri("/")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-Internal-Actor-Id", actorId.toString())
			// .header("X-Internal-Actor-Role", role)
			.bodyValue(body)
			.retrieve()
			.toBodilessEntity()
			.then();

		return accepted(req, action);
	}

	@DeleteMapping("/erase")
	public Mono<ResponseEntity<CommonResponse<Void>>> handleErase(
		@AuthenticationPrincipal Jwt jwt,
		ServerHttpRequest req,
		@RequestBody List<EraseRequest> body
	) {
		UUID actorId = ActorIdResolver.requireUserId(jwt);
		// String role  = jwt.getClaimAsString("role");

		Mono<Void> action = paintClient.method(org.springframework.http.HttpMethod.DELETE)
			.uri("/erase")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-Internal-Actor-Id", actorId.toString())
			// .header("X-Internal-Actor-Role", role)
			.bodyValue(body)
			.retrieve()
			.toBodilessEntity()
			.then();

		return accepted(req, action);
	}

	private Mono<ResponseEntity<CommonResponse<Void>>> accepted(ServerHttpRequest req, Mono<?> completion) {
		return completion.then(Mono.fromSupplier(() -> {
			CommonResponse<Void> response = new CommonResponse<>(
				LocalDateTime.now(),
				HttpStatus.ACCEPTED.value(),
				"ACCEPTED",
				"요청이 성공적으로 접수되었습니다.",
				req.getPath().value(),
				trace.currentTraceId(),
				null
			);
	
			return ResponseEntity
				.status(HttpStatus.ACCEPTED)
				.body(response);
		}));
	}
}
