package com.colombus.bff.web.api.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.user.contract.dto.AuthEventResponse;
import com.colombus.user.contract.dto.UserIdentityDto;
import com.colombus.user.contract.dto.UserProfileResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserViewController {
    
    private final WebClient userClient;
    private final TraceIdProvider trace;

    // 관리자용 유저 상세 조회
    @GetMapping("/{userId}")
    public Mono<ResponseEntity<CommonResponse<UserProfileResponse>>> getUserDetail(
        ServerHttpRequest request,
        @PathVariable UUID userId
    ) {
        Mono<UserProfileResponse> profile = userClient.get()
            .uri("/{userId}", userId)
            .retrieve()
            .bodyToMono(UserProfileResponse.class);

        Mono<ResponseEntity<CommonResponse<UserProfileResponse>>> notFound =
            notFound(request, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");

        return ok(request, profile)
            .switchIfEmpty(notFound)
            .onErrorResume(WebClientResponseException.NotFound.class, e -> notFound);
    }

    // 관리자용 유저 identities 조회
    @GetMapping("/{userId}/identities")
    public Mono<ResponseEntity<CommonResponse<List<UserIdentityDto>>>> getUserIdentities(
        ServerHttpRequest request,
        @PathVariable UUID userId
    ) {
        Mono<List<UserIdentityDto>> list = userClient.get()
            .uri("/{userId}/identities", userId)
            .retrieve()
            .bodyToFlux(UserIdentityDto.class)
            .collectList();

        return ok(request, list);
    }

    // 관리자용 auth event 조회
    @GetMapping("/{userId}/events")
    public Mono<ResponseEntity<CommonResponse<List<AuthEventResponse>>>> getUserEvents(
        ServerHttpRequest request,
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Instant beforeCreatedExclusive,
        @RequestParam(required = false) UUID beforeUuidExclusive
    ) {
        Mono<List<AuthEventResponse>> list = userClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/{userId}/events")
                .queryParam("limit", limit)
                .queryParamIfPresent("beforeCreatedExclusive",
                    Optional.ofNullable(beforeCreatedExclusive))
                .queryParamIfPresent("beforeUuidExclusive",
                    Optional.ofNullable(beforeUuidExclusive))
                .build(userId))
            .retrieve()
            .bodyToFlux(AuthEventResponse.class)
            .collectList();

        return ok(request, list);
    }

    // === helpers ===

    private <T> Mono<ResponseEntity<CommonResponse<T>>> ok(ServerHttpRequest request, Mono<T> data) {
        String path = request.getPath().value();
        String traceId = trace.currentTraceId();
        return data.map(d -> ResponseEntity.ok(CommonResponse.success(path, traceId, d)));
    }

    private <T> Mono<ResponseEntity<CommonResponse<T>>> notFound(ServerHttpRequest request, String code, String message) {
        return Mono.fromSupplier(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new CommonResponse<>(
                LocalDateTime.now(), 404, code, message, request.getPath().value(), trace.currentTraceId(), null
            )));
    }
}
