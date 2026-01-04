package com.colombus.bff.web.api.user;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.colombus.common.web.core.clientip.ClientIp;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserIdentityViewController {
    
    private final WebClient authClient;
    private final TraceIdProvider trace;

    @PostMapping("/link/start")
    public Mono<ResponseEntity<CommonResponse<Map<String, Object>>>> startLink(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String provider,   // "kakao", "google" 등 (auth-svc 쪽에서 String 처리)
        ServerHttpRequest req,
        @ClientIp String ip,
        @RequestHeader(name = "User-Agent", required = false) String ua
    ) {
        String bearer = (jwt != null ? jwt.getTokenValue() : null);

        return authClient.post()
            .uri("/internal/auth/{provider}:start-link", provider)
            .header("X-Client-Ip", ip)
            .header("X-Forwarded-For", ip)
            .header("User-Agent", ua != null ? ua : "")
            .headers(headers -> {
                if (bearer != null) {
                    headers.setBearerAuth(bearer);
                }
            })
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(res -> respond(req, HttpStatus.OK, "SUCCESS", "계정 링크를 시작했습니다.", Mono.just(res)));
    }

    @GetMapping("/oauth/link/callback")
    public Mono<ResponseEntity<CommonResponse<Map<String, Object>>>> oauthLinkCallback(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String code,
        @RequestParam String state,
        ServerHttpRequest req,
        @ClientIp String ip,
        @RequestHeader(name = "User-Agent", required = false) String ua
    ) {
        String bearer = (jwt != null ? jwt.getTokenValue() : null);

        return authClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/internal/auth/oauth/link/callback")
                .queryParam("code", code)
                .queryParam("state", state)
                .build())
            .header("X-Client-Ip", ip)
            .header("X-Forwarded-For", ip)
            .header("User-Agent", ua != null ? ua : "")
            .headers(headers -> {
                if (bearer != null) {
                    headers.setBearerAuth(bearer);
                }
            })
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(res -> {
                if (Boolean.TRUE.equals(res.get("linked"))) {
                    return respond(req, HttpStatus.OK, "SUCCESS", "계정 링크가 완료되었습니다.", Mono.just(res));
                }
                if (res.containsKey("error")) {
                    return respond(req, HttpStatus.BAD_REQUEST, "LINK_FAILED", "계정 링크에 실패했습니다.", Mono.just(res));
                }
                return respond(req, HttpStatus.OK, "SUCCESS", "요청이 처리되었습니다.", Mono.just(res));
            });
    }

    // === Common helpers ===

    private <T> Mono<ResponseEntity<CommonResponse<T>>> respond(
        ServerHttpRequest req, HttpStatus status, String code, String message, Mono<T> data
    ) {
        return data.map(d -> new CommonResponse<>(
                LocalDateTime.now(),
                status.value(),
                code,
                message,
                req.getPath().value(),
                trace.currentTraceId(),
                d
            ))
            .map(body -> ResponseEntity.status(status).body(body));
    }
}
