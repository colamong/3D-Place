package com.colombus.auth.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class InternalTokenIssuer {

    public record IssuedToken(String token, String jti, long expEpochSeconds) {}

    private final String issuer;
    private final long ttlSec;
    private final JwtEncoder encoder;
    private final InternalJwk jwk;

    public InternalTokenIssuer(
            @Value("${internal-jwt.issuer}") String issuer,
            @Value("${internal-jwt.ttl-seconds:300}") long ttlSec,
            InternalJwk jwk
    ) {
        this.issuer = issuer;
        this.ttlSec = ttlSec;
        this.jwk = jwk;
        this.encoder = jwk.jwtEncoder();
    }

    /** 유저 컨텍스트 토큰: sub = 내부 user UUID */
    public IssuedToken issueUser(
        Authentication auth,
        String aud,
        String scope,
        long ttlOverrideSec
    ) {
        String uid = resolveUidOrThrow(auth); // details.uid → JWT(uid/sub) 순

        JwtClaimsSet.Builder cb = baseClaims(aud, scope, ttlOverrideSec)
            .subject(uid)
            .claim("uid", uid);
        log.info("[InternalTokenIssuer] issueUser aud={}, scope={}, uid={}", aud, scope, uid);
        // Auth0 토큰이 들고 온 RBAC 정보를 내부 토큰에 복사해 downstream 서비스에서 재사용한다.
        copyAuth0Authorities(auth, cb);
        addIdpIfPresent(cb, auth);

        return encode(cb);
    }

    /** 서버→서버 토큰: sub = svc:<serviceName> (사용자 없음) */
    public IssuedToken issueService(
        String serviceName,
        String aud,
        String scope,
        long ttlOverrideSec
    ) {
        String svc = canonicalService(serviceName);
        JwtClaimsSet.Builder cb = baseClaims(aud, scope, ttlOverrideSec)
            .subject("svc:" + svc)
            .claim("act", "svc:" + svc);
        return encode(cb);
    }

    /** On-Behalf-Of: 사용자 sub + act=호출 서비스(선택 기능) */
    public IssuedToken issueOnBehalf(
        Authentication userAuth,
        String actorService,
        String aud,
        String scope,
        long ttlOverrideSec
    ) {
        String uid = resolveUidOrThrow(userAuth);
        String actor = "svc:" + canonicalService(actorService);

        JwtClaimsSet.Builder cb = baseClaims(aud, scope, ttlOverrideSec)
            .subject(uid)
            .claim("uid", uid)
            .claim("act", actor)
            .claim("obo", true);

        // OBO 토큰도 사용자 권한을 그대로 물고 가야 하므로 동일하게 Auth0 클레임을 옮긴다.
        copyAuth0Authorities(userAuth, cb);

        addIdpIfPresent(cb, userAuth);
        return encode(cb);
    }

    @Deprecated
    public IssuedToken issueWithMeta(Authentication auth, String aud, String scope, long ttlOverrideSec) {
        // uid가 있으면 유저 토큰, 없으면 S2S로 간주
        String uid = tryResolveUid(auth);
        if (uid != null) {
            return issueUser(auth, aud, scope, ttlOverrideSec);
        } else {
            return issueService("unknown", aud, scope, ttlOverrideSec);
        }
    }

    
    private JwtClaimsSet.Builder baseClaims(String aud, String scope, long ttlOverrideSec) {
        Instant now = Instant.now();
        long ttl = (ttlOverrideSec > 0 ? ttlOverrideSec : defaultTtlSec());
        JwtClaimsSet.Builder cb = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .issuer(issuer)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttl));
        if (aud != null && !aud.isBlank()) cb.audience(List.of(aud));
        if (scope != null && !scope.isBlank()) cb.claim("scope", scope);
        return cb;
    }

    private IssuedToken encode(JwtClaimsSet.Builder cb) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwk.keyId()).build();
        JwtClaimsSet claims = cb.build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedToken(token, claims.getId(), claims.getExpiresAt().getEpochSecond());
    }

    private void copyAuth0Authorities(Authentication auth, JwtClaimsSet.Builder cb) {
        if (!(auth instanceof JwtAuthenticationToken jat)) {
            return;
        }

        var jwt = jat.getToken();

        // Auth0 RBAC permissions → 내부 토큰 permissions
        var perms = jwt.getClaimAsStringList("permissions");
        if (perms != null && !perms.isEmpty()) {
            cb.claim("permissions", perms);
        }

        // Auth0 roles 클레임이 있다면 그대로 복사
        var roles = jwt.getClaimAsStringList("roles");
        if (roles != null && !roles.isEmpty()) {
            cb.claim("roles", roles);
        }

        // 외부 scope는 별도로 보관 (내부 scope는 need 기반이라 유지)
        String extScope = jwt.getClaimAsString("scope");
        if (extScope != null && !extScope.isBlank()) {
            cb.claim("idp_scope", extScope);
        }
    }

    private void addIdpIfPresent(JwtClaimsSet.Builder cb, Authentication auth) {
        Object d = (auth != null) ? auth.getDetails() : null;
        if (d instanceof Map<?,?> m) {
            Object idp = m.get("idp");
            if (idp instanceof Map<?,?> idpMap) {
                cb.claim("idp", Map.of(
                    "provider", String.valueOf(idpMap.get("provider")),
                    "tenant",   String.valueOf(idpMap.get("tenant")),
                    "sub",      String.valueOf(idpMap.get("sub"))
                ));
            }
        }
    }

    private String resolveUidOrThrow(Authentication auth) {
        String uid = tryResolveUid(auth);
        if (uid == null || uid.isBlank()) {
            log.info("[InternalTokenIssuer] uid is null");
            throw new InsufficientAuthenticationException("Cannot resolve internal uid");
        }
        return uid;
    }

    private String tryResolveUid(Authentication auth) {
        if (auth == null) return null;

        // details.uid
        Object d = auth.getDetails();
        if (d instanceof Map<?,?> m) {
            Object u = m.get("uid");
            if (u instanceof String s && !s.isBlank()) return s;
        }

        // JWT(uid) → sub
        if (auth instanceof JwtAuthenticationToken jat) {
            var jwt = jat.getToken();
            String uid = jwt.getClaimAsString("uid");
            if (uid == null || uid.isBlank()) uid = jwt.getSubject();
            if (uid != null && !uid.isBlank()) return uid;
        }

        if (auth instanceof OAuth2AuthenticationToken oat) {
            var principal = oat.getPrincipal();

            if (principal instanceof OidcUser oidc) {
                Object u = oidc.getClaims().get("uid");
                if (u instanceof String s && !s.isBlank()) {
                    return s;
                }
            } else if (principal instanceof OAuth2User oUser) {
                Object u = oUser.getAttributes().get("uid");
                if (u instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }

        return null;
    }

    private String canonicalService(String name) {
        return (name == null || name.isBlank()) ? "unknown" : name.trim().toLowerCase();
    }

    public long defaultTtlSec() { return this.ttlSec; }

}
