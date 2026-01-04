package com.colombus.bff.web.api.user;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.bind.annotation.DeleteMapping;
import reactor.core.publisher.Mono;
import com.colombus.bff.util.ActorIdResolver;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.user.contract.dto.PublicProfileResponse;
import com.colombus.user.contract.dto.UpdateProfileRequest;
import com.colombus.user.contract.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileViewController {

    private final WebClient userClient;
    private final TraceIdProvider trace;

    // 본인 프로필 조회
    @GetMapping("/me")
    public Mono<ResponseEntity<CommonResponse<UserProfileResponse>>> me(
        @AuthenticationPrincipal Jwt jwt,
        ServerHttpRequest req
    ) {
        UUID actorId = ActorIdResolver.requireUserId(jwt);

        Mono<UserProfileResponse> profile = userClient.get()
            .uri("/me")
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .bodyToMono(UserProfileResponse.class);

        Mono<ResponseEntity<CommonResponse<UserProfileResponse>>> notFound =
            notFound(req, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");

        return ok(req, profile)
            .switchIfEmpty(notFound)
            .onErrorResume(WebClientResponseException.NotFound.class, e -> notFound);
    }

    // 공개용 프로필
    @GetMapping("/profile/{nicknameHandle}")
    public Mono<ResponseEntity<CommonResponse<PublicProfileResponse>>> publicProfile(
        @AuthenticationPrincipal Jwt jwt,
        ServerHttpRequest req,
        @PathVariable String nicknameHandle
    ) {
        Mono<PublicProfileResponse> profile = userClient.get()
            .uri("/profile/{nicknameHandle}", nicknameHandle)
            .retrieve()
            .bodyToMono(PublicProfileResponse.class);

        Mono<ResponseEntity<CommonResponse<PublicProfileResponse>>> notFound =
            notFound(req, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");

        return ok(req, profile)
            .switchIfEmpty(notFound)
            .onErrorResume(WebClientResponseException.NotFound.class, e -> notFound);
    }

    // 본인 프로필 수정
    @PostMapping("/profile")
    public Mono<ResponseEntity<CommonResponse<UserProfileResponse>>> updateProfile(
        @AuthenticationPrincipal Jwt jwt,
        ServerHttpRequest req,
        @RequestBody UpdateProfileRequest body
    ) {
        UUID actorId = ActorIdResolver.requireUserId(jwt);

        Mono<UserProfileResponse> result = userClient.post()
            .uri("/profile")
            .header("X-Internal-Actor-Id", actorId.toString())
            .bodyValue(body)
            .retrieve()
            .bodyToMono(UserProfileResponse.class);

        return result.map(res -> respond(req, HttpStatus.OK, "SUCCESS", "요청이 성공적으로 처리되었습니다.", res));
    }

    // 본인 탈퇴
    @DeleteMapping("/me")
    public Mono<ResponseEntity<Void>> deactivateMe(
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID actorId = ActorIdResolver.requireUserId(jwt);
        return userClient.delete()
            .uri("/deactivate")
            .header("X-Internal-Actor-Id", actorId.toString())
            .retrieve()
            .bodyToMono(Void.class)
            .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // === Common helpers ===

    private <T> Mono<ResponseEntity<CommonResponse<T>>> ok(ServerHttpRequest req, Mono<T> data) {
        String path = req.getPath().value();
        String traceId = trace.currentTraceId();
        return data.map(d -> ResponseEntity.ok(CommonResponse.success(path, traceId, d)));
    }

    private <T> ResponseEntity<CommonResponse<T>> respond(
        ServerHttpRequest req, HttpStatus status, String code, String message, T data
    ) {
        var body = new CommonResponse<>(
            LocalDateTime.now(),
            status.value(),
            code,
            message,
            req.getPath().value(),
            trace.currentTraceId(),
            data
        );
        return ResponseEntity.status(status).body(body);
    }

    private <T> Mono<ResponseEntity<CommonResponse<T>>> notFound(ServerHttpRequest req, String code, String message) {
        return Mono.fromSupplier(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new CommonResponse<>(
                LocalDateTime.now(), 404, code, message, req.getPath().value(), trace.currentTraceId(), null
            )));
    }
}
