package com.colombus.auth.web.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colombus.auth.infra.auth0.Auth0MgmtClient;

import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/internal/auth")
@Validated
@RequiredArgsConstructor
public class VerifyEmailController {

    private final Auth0MgmtClient mgmt;
    public record ResendReq(@Email String email) {}

    /**
     * 이메일 인증 메일 재전송 (internal)
     */
    @PostMapping("/verify-email/resend")
    public Mono<Map<String, Object>> resend(@Valid @RequestBody ResendReq req) {
        if (req.email() == null || req.email().isBlank()) {
            return Mono.just(Map.<String, Object>of("ok", false, "error", "email_required"));
        }

        return mgmt.findDbUserByEmail(req.email())
            .flatMap(user -> mgmt.sendVerificationEmail(user.user_id)
                .thenReturn(Map.<String, Object>of("ok", true, "email", req.email())))
            .switchIfEmpty(Mono.just(Map.<String, Object>of(
                "ok", false,
                "error", "not_db_user",
                "message", "해당 이메일의 DB 사용자(Username-Password)가 없습니다. 소셜/SSO 사용자는 제공자에서 인증하세요."
            )))
            .onErrorResume(e -> {
                log.error("resend verification failed for {}", req.email(), e);
                return Mono.just(Map.<String, Object>of("ok", false, "error", "resend_failed"));
            });
    }

    /**
     * 이메일 / 유저 상태 조회 (internal)
     */
    @GetMapping("/verify-email/context")
    public Mono<Map<String, Object>> context(@RequestParam("email") String email) {
        return mgmt.findDbUserByEmail(email)
            .flatMap(user -> Mono.just(buildContext(user)))
            .switchIfEmpty(Mono.just(Map.<String, Object>of(
                "kind", "unknown",
                "dbUser", false
            )));
    }

    private Map<String, Object> buildContext(Auth0MgmtClient.User u) {
        boolean verified = Boolean.TRUE.equals(u.email_verified);

        Instant created = null;
        try {
            if (u.created_at != null) {
                created = Instant.parse(u.created_at);
            }
        } catch (Exception ignore) {}

        boolean recent = false;
        if (created != null) {
            recent = Duration.between(created, Instant.now())
                .abs()
                .toHours() < 24;
        }

        String kind = verified ? "already_verified" : (recent ? "signup" : "existing_unverified");

        return Map.<String, Object>of(
            "kind", kind,
            "dbUser", true,
            "email_verified", verified,
            "created_at", u.created_at
        );
    }
}
