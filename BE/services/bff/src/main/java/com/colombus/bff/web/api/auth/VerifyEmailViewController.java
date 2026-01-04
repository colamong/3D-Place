package com.colombus.bff.web.api.auth;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.colombus.common.web.core.clientip.ClientIp;
import com.colombus.common.web.core.response.CommonResponse;
import com.colombus.common.web.core.tracing.TraceIdProvider;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class VerifyEmailViewController {
    
    private final WebClient authClient; // auth-svc baseUrl 로 설정된 WebClient
    private final TraceIdProvider trace;

    public record ResendReq(@Email String email) {}

    /**
     * 이메일 인증 메일 재전송 (SPA -> BFF -> auth-svc)
     */
    @PostMapping("/verify-email/resend")
    public Mono<ResponseEntity<CommonResponse<Map<String, Object>>>> resend(
        @Valid @RequestBody ResendReq body,
        ServerHttpRequest request,
        @ClientIp String ip,
        @RequestHeader(name = "User-Agent", required = false) String ua
    ) {
        return authClient.post()
            .uri("/verify-email/resend")
            .header("X-Client-Ip", ip)
            .header("X-Forwarded-For", ip)
            .header("User-Agent", ua != null ? ua : "")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(result -> {
                boolean ok = Boolean.TRUE.equals(result.get("ok"));
                if (!ok) {
                    String code = String.valueOf(result.getOrDefault("error", "resend_failed")).toUpperCase();
                    return Mono.fromSupplier(() -> respond(request, HttpStatus.BAD_REQUEST, code, "인증 메일 재전송에 실패했습니다.", result));
                }
                return Mono.fromSupplier(() -> respond(request, HttpStatus.OK, "SUCCESS", "인증 메일을 다시 보냈습니다.", result));
            });
    }

    /**
     * 이메일 / 유저 상태 조회 (SPA -> BFF -> auth-svc)
     */
    @GetMapping("/verify-email/context")
    public Mono<ResponseEntity<CommonResponse<Map<String, Object>>>> context(
        @RequestParam("email") String email,
        ServerHttpRequest request,
        @ClientIp String ip,
        @RequestHeader(name = "User-Agent", required = false) String ua
    ) {
        return authClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/verify-email/context")
                .queryParam("email", email)
                .build())
            .header("X-Client-Ip", ip)
            .header("X-Forwarded-For", ip)
            .header("User-Agent", ua != null ? ua : "")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .map(result -> respond(request, HttpStatus.OK, "SUCCESS", "이메일 상태를 조회했습니다.", result));
    }

    // === Common helpers ===

    private <T> ResponseEntity<CommonResponse<T>> respond(
        ServerHttpRequest request, HttpStatus status, String code, String message, T data
    ) {
        var body = new CommonResponse<>(
            LocalDateTime.now(),
            status.value(),
            code,
            message,
            request.getPath().value(),
            trace.currentTraceId(),
            data
        );
        return ResponseEntity.status(status).body(body);
    }
}
