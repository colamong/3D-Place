package com.colombus.auth.security.auth;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.colombus.auth.infra.usersvc.UserSvcClient;
import com.colombus.user.contract.enums.AuthProviderCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessEnsureListener {

    private final UserSvcClient userSvcClient;
    private final ReactiveClientRegistrationRepository clientRegistrations;

    public Mono<Void> ensure(ServerWebExchange exchange, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return Mono.empty();
        }

        return synchronizeUser(token)
            .flatMap(result -> exchange.getSession()
                .doOnNext(session -> session.getAttributes().put("S_UID", result.uid()))
                .then(Mono.fromRunnable(() -> applyDetails(token, result))))
            .onErrorResume(ex -> {
                log.error("ensure call failed, will rely on next request to retry. sub={}", token.getName(), ex);
                return Mono.<Void>empty();
            })
            .then();
    }

    private Mono<EnsurePayload> synchronizeUser(OAuth2AuthenticationToken token) {
        return extractPrincipal(token)
            .flatMap(payload -> {
                AuthProviderCode provider = providerFrom(payload.sub(), token);
                return userSvcClient.ensureUser(provider, payload.tenant(), payload.sub(), payload.email(), Boolean.TRUE.equals(payload.emailVerified()), payload.avatarUrl())
                    .doOnNext(res -> log.debug("ensure synced to user-svc: sub={}, provider={}, tenant={}", payload.sub(), provider, payload.tenant()))
                    .map(res -> new EnsurePayload(res.userId().toString(), provider, payload.tenant(), payload.sub()));
            });
    }

    private void applyDetails(OAuth2AuthenticationToken token, EnsurePayload payload) {
        var newDetails = new HashMap<String, Object>();
        newDetails.put("web", token.getDetails());
        newDetails.put("uid", payload.uid());
        newDetails.put("idp", Map.of(
            "provider", payload.provider(),
            "tenant", payload.tenant(),
            "sub", payload.sub()
        ));
        token.setDetails(newDetails);
    }

    private Mono<PrincipalPayload> extractPrincipal(OAuth2AuthenticationToken token) {
        String sub = null, email = null, iss = null, avatarUrl = null;
        Boolean verified = null;

        var principal = token.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            sub = oidc.getSubject();
            email = oidc.getEmail();
            verified = oidc.getEmailVerified();
            iss = oidc.getIssuer() != null ? oidc.getIssuer().toString() : null;
            avatarUrl = oidc.getPicture();
        } else if (principal instanceof DefaultOAuth2User ou) {
            var attrs = ou.getAttributes();
            sub = String.valueOf(attrs.getOrDefault("sub", attrs.get("id")));
            email = (String) attrs.get("email");
            Object evf = attrs.get("email_verified");
            verified = (evf instanceof Boolean) ? (Boolean) evf : null;
            iss = (String) attrs.get("iss");

            Object pic = attrs.get("picture");
            if (pic == null) {
                pic = attrs.get("avatar_url");
            }
            avatarUrl = (pic != null ? pic.toString() : null);
        } else {
            throw new IllegalStateException("Unsupported principal type: " + principal.getClass().getName());
        }

        final String subject = sub;
        final String mail = email;
        final Boolean verifiedVal = verified;
        final String avatar = avatarUrl;

        return tenantFromIssuer(iss, token)
            .map(tenant -> new PrincipalPayload(subject, mail, verifiedVal, tenant, avatar))
            .switchIfEmpty(Mono.just(new PrincipalPayload(subject, mail, verifiedVal, null, avatar)));
    }

    private Mono<String> tenantFromIssuer(String iss, OAuth2AuthenticationToken token) {
        if (iss != null) {
            try {
                String host = URI.create(iss).getHost();
                if (host != null && !host.isBlank()) {
                    return Mono.just(host);
                }
            } catch (Exception ignore) {
                // fallback below
            }
        }

        String regId = token.getAuthorizedClientRegistrationId();
        return clientRegistrations.findByRegistrationId(regId)
            .map(reg -> firstHost(reg.getProviderDetails().getIssuerUri(),
                reg.getProviderDetails().getAuthorizationUri()))
            .flatMap(host -> host != null && !host.isBlank()
                ? Mono.just(host)
                : Mono.empty());
    }

    private static String firstHost(String... uris) {
        for (String uri : uris) {
            if (uri == null || uri.isBlank()) continue;
            try {
                String host = URI.create(uri).getHost();
                if (host != null && !host.isBlank()) {
                    return host;
                }
            } catch (Exception ignore) {}
        }
        return null;
    }

    private AuthProviderCode providerFrom(String sub, OAuth2AuthenticationToken token) {
        String reg = token.getAuthorizedClientRegistrationId(); // "auth0", "google", ...
        return switch (reg.toLowerCase()) {
            case "auth0"      -> AuthProviderCode.AUTH0;
            case "google"     -> AuthProviderCode.GOOGLE;
            case "kakao"      -> AuthProviderCode.KAKAO;
            default           -> AuthProviderCode.GUEST;
        };
    }

    private record PrincipalPayload(String sub, String email, Boolean emailVerified, String tenant, String avatarUrl) {}
    private record EnsurePayload(String uid, AuthProviderCode provider, String tenant, String sub) {}
}
