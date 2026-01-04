package com.colombus.auth.infra.usersvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.colombus.auth.infra.usersvc.dto.EnsureResult;
import com.colombus.auth.infra.usersvc.dto.IdentityDto;
import com.colombus.auth.security.jwt.InternalTokenIssuer;
import com.colombus.auth.security.support.IdpResolver;
import com.colombus.auth.security.support.IdpResolver.IdpTriple;
import com.colombus.user.contract.dto.AuthEventByIdentityRequest;
import com.colombus.user.contract.dto.ExternalIdentityPayload;
import com.colombus.user.contract.enums.AuthEventKindCode;
import com.colombus.user.contract.enums.AuthProviderCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSvcClient {

    private final WebClient userClient;
    private final InternalTokenIssuer issuer;

    // 로그아웃 기록은 user-svc가 감사 이벤트를 쌓도록 위임한다(실패해도 auth 흐름은 계속됨).
    public Mono<Void> recordLogout(
        Authentication auth,
        OidcUser user,
        String issuerUri,
        String ip,
        String ua
    ) {
        if (auth == null || user == null) return Mono.empty();

        IdpTriple idp = IdpResolver.resolveIdp(auth, user, issuerUri);

        // DTO로 안전 전송 (providerTenant는 non-null 보장)
        AuthEventByIdentityRequest body = new AuthEventByIdentityRequest(
                idp.provider(),
                idp.tenant(),
                idp.sub(),
                AuthEventKindCode.LOGOUT,
                (ip != null || ua != null) ? ("ip=" + String.valueOf(ip) + ", ua=" + String.valueOf(ua)) : null
        );

        var token = issuer.issueService("authsvc", "user-svc", "user:write", 120);

        return userClient.post()
                .uri("/auth-events")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.token())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                // 감사 로깅 실패는 로그아웃을 막지 않음
                .onErrorResume(e -> {
                    log.warn("recordLogout failed (ignored): {}", e.toString());
                    return Mono.empty();
                });
    }

    public Mono<EnsureResult> ensureUser(
        AuthProviderCode provider, String tenant, String sub,
        String email, boolean emailVerified, String avatarUrl
    ) {

        var token = issuer.issueService("authsvc", "user-svc", "user:write", 120);

        Map<String, Object> body = Map.of(
            "provider",        provider,
            "providerTenant",  tenant,
            "providerSub",     sub,
            "email",           email,
            "emailVerified",   emailVerified,
            "avatarUrl",       avatarUrl
        );

        return userClient.post()
           .uri("/ensure")
           .contentType(MediaType.APPLICATION_JSON)
           .header("Authorization", "Bearer " + token.token())
           .bodyValue(body)
           .retrieve()
           .bodyToMono(EnsureResult.class);
    }

    public Mono<EnsureResult> ensureLink(UUID userId, String provider, String tenant, String sub, String bearer) {

        Map<String, Object> body = Map.of(
            "userId",          userId,
            "provider",        provider,
            "providerTenant",  tenant,
            "providerSub",     sub
        );

        return userClient.post()
           .uri("/link")
           .contentType(MediaType.APPLICATION_JSON)
           .header("Authorization", "Bearer " + bearer)
           .bodyValue(body)
           .retrieve()
           .bodyToMono(EnsureResult.class);
    }

    // TODO: 수정 필
    public Mono<Void> ensureUnlink(UUID userId, String provider, String tenant, String sub) {
        var token = issuer.issueService("authsvc", "user-svc", "user:read", 120);

        return userClient.post()
            .uri("/{userId}/identities:unlink", userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.token())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "provider", provider,
                "providerTenant", tenant,
                "providerSub", sub
            ))
            .retrieve()
            .bodyToMono(Void.class);
    }

    public Mono<IdentityDto> getIdentity(UUID userId, UUID identityId) {
        var token = issuer.issueService("authsvc", "user-svc", "user:read", 120);

        return userClient.get()
            .uri("/{userId}/identities/{id}", userId, identityId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.token())
            .retrieve()
            .bodyToMono(IdentityDto.class);
    }

    public Mono<IdentityDto> getIdentityByProvider(UUID userId, String provider) {
        var token = issuer.issueService("authsvc", "user-svc", "user:read", 120);

        return userClient.get()
            .uri("/{userId}/provider/{provider}", userId, provider)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.token())
            .retrieve()
            .bodyToMono(IdentityDto.class)
            .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

    public Mono<List<ExternalIdentityPayload>> getIdentities(UUID userId) {
        var token = issuer.issueService("authsvc", "user-svc", "user:read", 120);

        return userClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/{userId}/identities")
                .build(userId)
            )
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.token())
            .retrieve()
            .bodyToFlux(ExternalIdentityPayload.class)
            .collectList();
    }
}
