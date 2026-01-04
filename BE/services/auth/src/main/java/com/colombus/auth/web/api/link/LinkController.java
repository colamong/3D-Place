package com.colombus.auth.web.api.link;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.colombus.auth.application.link.LinkService;
import com.colombus.auth.application.link.LinkService.LinkSecurityException;
import com.colombus.auth.application.link.LinkService.LinkStartRes;
import com.colombus.auth.application.link.LinkService.LinkStateException;
import com.colombus.auth.infra.auth0.exception.Auth0BadRequestException;
import com.colombus.auth.infra.auth0.exception.Auth0ConflictException;
import com.colombus.auth.infra.auth0.exception.Auth0NotFoundException;
import com.colombus.auth.infra.auth0.exception.Auth0ServerException;
import com.colombus.auth.infra.auth0.exception.Auth0UnauthorizedException;
import com.colombus.auth.security.jwt.InternalTokenIssuer;
import com.colombus.auth.web.support.CurrentUserResolver;
import com.colombus.common.web.core.clientip.ClientIp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Slf4j
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService service;
    private final CurrentUserResolver currentUser;
    private final InternalTokenIssuer internal;

    @PostMapping("/{provider}:start-link")
    // 게이트웨이에서 프록시되더라도 실제 redirectUri 계산/세션 검사는 auth 서비스가 맡는다.
    public Mono<LinkStartRes> start(
        @ClientIp String ip,
        @RequestHeader(value="User-Agent", required=false) String ua,
        @PathVariable String provider,
        Authentication auth,
        ServerHttpRequest req
    ) {
        log.info("[LinkController] auth={}", auth);
        UUID userId = currentUser.platformUserId(auth);
        String primarySub = currentUser.primaryAuth0UserId(auth);
        String redirectUri = buildRedirectUri(req, provider);
        return service.startLink(userId, primarySub, provider, ip, ua, redirectUri);
    }

    @GetMapping("/oauth/link/callback")
    public Mono<ResponseEntity<Map<String, Object>>> callback(
        @RequestParam String code,
        @RequestParam String state,
        @ClientIp String ip,
        @RequestHeader(value = "User-Agent", required = false) String ua,
        Authentication auth
    ) {
        UUID userId = currentUser.platformUserId(auth);
        String primarySub = currentUser.primaryAuth0UserId(auth);
        var token = internal.issueService("authsvc", "user-svc", "user:write", 120);

        return service.completeLink(userId, primarySub, code, state, ip, ua, token.token())
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of("linked", Boolean.TRUE)))
            .onErrorResume(LinkStateException.class, e -> Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of("error", e.getMessage()))))
            .onErrorResume(LinkSecurityException.class, e -> Mono.just(ResponseEntity.status(403).body(Map.<String, Object>of("error", e.getMessage()))))
            .onErrorResume(Auth0ConflictException.class, e -> Mono.just(ResponseEntity.ok(Map.<String, Object>of("linked", Boolean.TRUE))))
            .onErrorResume(Auth0NotFoundException.class, e -> Mono.just(ResponseEntity.status(404).body(Map.<String, Object>of("error", "identity_not_found_at_provider"))))
            .onErrorResume(Auth0UnauthorizedException.class, e -> Mono.just(ResponseEntity.status(502).body(Map.<String, Object>of("error", "auth0_unauthorized"))))
            .onErrorResume(Auth0BadRequestException.class, e -> Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of("error", "auth0_bad_request"))))
            .onErrorResume(Auth0ServerException.class, e -> Mono.just(ResponseEntity.status(502).body(Map.<String, Object>of("error", "auth0_link_failed"))));
    }

    @PostMapping("/{provider}:unlink")
    public Mono<ResponseEntity<Map<String, Object>>> unlink(
        @PathVariable String provider,
        Authentication auth
    ) {
        UUID userId = currentUser.platformUserId(auth);
        String primarySub = currentUser.primaryAuth0UserId(auth);
        var token = internal.issueService("authsvc", "user-svc", "user:write", 120);

        return service.unlinkByProvider(userId, primarySub, provider, token.token())
            .thenReturn(ResponseEntity.ok(Map.<String, Object>of("unlinked", Boolean.TRUE)))
            .onErrorResume(Auth0NotFoundException.class, e -> Mono.just(ResponseEntity.status(404).body(Map.<String, Object>of("error", "identity_not_found_at_provider"))))
            .onErrorResume(Auth0UnauthorizedException.class, e -> Mono.just(ResponseEntity.status(502).body(Map.<String, Object>of("error", "auth0_unauthorized"))))
            .onErrorResume(Auth0BadRequestException.class, e -> Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of("error", "auth0_bad_request"))))
            .onErrorResume(Auth0ServerException.class, e -> Mono.just(ResponseEntity.status(502).body(Map.<String, Object>of("error", "auth0_unlink_failed"))));
    }

    private static String buildRedirectUri(ServerHttpRequest req, String provider) {
        // TODO: 나중에 퍼블릭 도메인으로 변경해야함
        // String base = req.getRequestURL().toString().replace(req.getRequestURI(), "");
        String base = "http://localhost:8080";
        return base + "/api/users/oauth/link/callback";
    }

}
