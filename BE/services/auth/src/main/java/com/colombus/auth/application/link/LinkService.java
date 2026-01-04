package com.colombus.auth.application.link;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.colombus.auth.infra.auth0.Auth0MgmtClient;
import com.colombus.auth.infra.auth0.exception.Auth0ConflictException;
import com.colombus.auth.infra.oidc.LinkOidcClient;
import com.colombus.auth.infra.oidc.LinkOidcClient.OidcTokens;
import com.colombus.auth.infra.session.LinkSession;
import com.colombus.auth.infra.session.LinkSessionStore;
import com.colombus.auth.infra.usersvc.UserSvcClient;
import com.colombus.common.utility.crypto.Hashes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkService {

    private final LinkSessionStore linkSessions;
    private final LinkOidcClient oidc;
    private final Auth0MgmtClient mgmt;
    private final UserSvcClient usersvc;
    private final LinkProperties props;

    private static final Duration TTL = Duration.ofMinutes(10);

    /** 시작: state/PKCE 세션 발급 + authorize URL 생성 */
    public Mono<LinkStartRes> startLink(UUID userId, String primaryAuth0Sub, String provider, String ip, String ua, String redirectUri) {
        ensurePrimaryIsAuth0(primaryAuth0Sub);
        String normalized = normalizeProvider(provider);
        ensureAllowed(normalized);

        String ipHash = Hashes.sha256Base64Url(ip);
        String uaHash = Hashes.sha256Base64Url(ua);

        return linkSessions.issue(userId, normalized, redirectUri, TTL, ipHash, uaHash)
            .doOnNext(s -> log.info("[linkService] LinkSession// state={}, codeChallenge={}, redirectUri={}, normalized={}", s.state(), s.codeChallenge(), s.redirectUri(), normalized))
            .flatMap(s -> oidc.buildAuthorizeUrl("auth0", s.state(), s.codeChallenge(), s.redirectUri(), normalized)
                .map(authorizeUrl -> {
                    log.info("[LinkService] authorizeUrl={}, state={}", authorizeUrl, s.state());
                    return new LinkStartRes(authorizeUrl, s.state());
                }));
    }

    /** 콜백: 코드교환 → mgmt 링크 → 내부 ensure */
    public Mono<Void> completeLink(
        UUID userId, String primaryAuth0Sub,
        String code, String state, String ip, String ua,
        String internalBearer
    ) {
        return linkSessions.consume(state)
            .switchIfEmpty(Mono.error(new LinkStateException("invalid_or_expired_state")))
            .flatMap(s -> {
                ensurePrimaryIsAuth0(primaryAuth0Sub);
                String normalized = normalizeProvider(s.provider());
                ensureAllowed(normalized);

                if (!s.userId().equals(userId)) {
                    return Mono.error(new LinkSecurityException("state_user_mismatch"));
                }
                if (s.ipHash() != null && ip != null &&
                    !Hashes.sha256Base64Url(ip).equals(s.ipHash())) {
                    return Mono.error(new LinkSecurityException("ip_mismatch"));
                }
                if (s.uaHash() != null && ua != null &&
                    !Hashes.sha256Base64Url(ua).equals(s.uaHash())) {
                    return Mono.error(new LinkSecurityException("ua_mismatch"));
                }

                return oidc.exchangeCode("auth0", code, s.codeVerifier(), s.redirectUri())
                    .flatMap(tokens -> linkAccounts(primaryAuth0Sub, normalized, s, tokens, internalBearer));
            });
    }

    public Mono<Void> unlinkByProvider(UUID userId, String primaryAuth0Sub, String provider, String bearer) {
        ensurePrimaryIsAuth0(primaryAuth0Sub);

        String normalized = normalizeProvider(provider);
        ensureAllowed(normalized);

        return usersvc.getIdentityByProvider(userId, normalized)
            .switchIfEmpty(Mono.error(new IdentityNotLinked()))
            .flatMap(id -> {
                String providerUserId = extractProviderSub(id.provider(), id.sub());
                return mgmt.unlinkIdentity(primaryAuth0Sub, normalized, providerUserId)
                    .then(usersvc.ensureUnlink(userId, normalized, id.tenant(), id.sub()));
            });
    }

    public static class IdentityNotLinked extends RuntimeException {}

    private static String extractProviderSub(String provider, String rawSub) {
        int bar = rawSub == null ? -1 : rawSub.indexOf('|');
        return (bar >= 0 && bar + 1 < rawSub.length()) ? rawSub.substring(bar + 1) : rawSub;
    }
    private static String extractTenant(String provider, Jwt idToken) {
        String tid = idToken.getClaimAsString("tid"); // Azure AD 계열
        return (tid != null && !tid.isBlank()) ? tid : "default";
    }

    private static void ensurePrimaryIsAuth0(String sub) {
        if (sub == null || !sub.startsWith("auth0|")) throw new IllegalStateException("primary_not_auth0");
    }
    private String normalizeProvider(String p) {
        return p == null ? "" : p.toLowerCase(Locale.ROOT).trim();
    }
    private void ensureAllowed(String provider) {
        if (!props.getAllowedConnections().contains(provider)) {
            throw new IllegalArgumentException("unsupported_provider");
        }
    }

    private Mono<Void> linkAccounts(
        String primaryAuth0Sub,
        String normalized,
        LinkSession session,
        OidcTokens tokens,
        String internalBearer
    ) {
        String idToken = tokens.idToken();
        return mgmt.linkWithIdToken(primaryAuth0Sub, idToken)
            .onErrorResume(Auth0ConflictException.class, e -> Mono.empty())
            .then(oidc.decodeIdToken("auth0", idToken))
            .flatMap(jwt -> {
                String tenant = extractTenant(normalized, jwt);
                String sub = extractProviderSub(normalized, jwt.getSubject());
                return usersvc.ensureLink(session.userId(), normalized, tenant, sub, internalBearer).then();
            });
    }

    public record LinkStartRes(String authorizeUrl, String state) {}
    public static class LinkStateException extends RuntimeException { public LinkStateException(String m){super(m);} }
    public static class LinkSecurityException extends RuntimeException { public LinkSecurityException(String m){super(m);} }
}
