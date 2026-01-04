package com.colombus.auth.security.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import com.colombus.common.utility.crypto.Hashes;
import com.colombus.common.utility.text.Texts;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class InternalTokenCacheService {

    private final ReactiveStringRedisTemplate redis;
    private final ReactiveValueOperations<String, String> values;
    private final InternalTokenIssuer issuer;
    private final ObjectMapper objectMapper;

    public InternalTokenCacheService(
        ReactiveStringRedisTemplate redis,
        InternalTokenIssuer issuer,
        ObjectMapper objectMapper
    ) {
        this.redis = redis;
        this.values = redis.opsForValue();
        this.issuer = issuer;
        this.objectMapper = objectMapper;
    }

    @Value("${internal-jwt-cache.key-prefix:authsvc:it}")
    private String keyPrefix;

    @Value("${internal-jwt-cache.refresh-window-seconds:60}")
    private long refreshWindowSec;

    /** 유저 토큰 발급/재사용 */
    public Mono<String> issueOrReuseUser(Authentication auth, String aud, String need, long ttlOverrideSec) {
        return issueOrReuseWith(auth, aud, need, ttlOverrideSec,
            () -> issuer.issueUser(auth, aud, need, ttlOverrideSec));
    }

    /** S2S 토큰 발급/재사용 (serviceName을 sub로 사용) */
    public Mono<String> issueOrReuseService(Authentication auth, String serviceName, String aud, String need, long ttlOverrideSec) {
        // 캐시 키는 여전히 호출자(Authentication) 기준으로 묶임 → 게이트웨이/프록시별로 분리됨
        return issueOrReuseWith(auth, aud, need, ttlOverrideSec,
            () -> issuer.issueService(serviceName, aud, need, ttlOverrideSec));
    }

    // (기존) 범용 — 외부 호출 금지
    @Deprecated
    public Mono<String> issueOrReuse(Authentication auth, String aud, String need, long ttlOverrideSec) {
        return issueOrReuseWith(auth, aud, need, ttlOverrideSec,
            () -> issuer.issueWithMeta(auth, aud, need, ttlOverrideSec));
    }

    // ===== 공통 캐싱 로직 =====
    private Mono<String> issueOrReuseWith(Authentication auth, String aud, String need, long ttlOverrideSec,
                                    Supplier<InternalTokenIssuer.IssuedToken> issuerFn) {
        String src = sourceKey(auth);
        String key = cacheKey(aud, need, src);
        return getFreshToken(key)
            .switchIfEmpty(Mono.defer(() -> {
                log.info("[InternalTokenCache] miss or stale. key={} → enforceWithLock", key);
                return enforceWithLock(key, issuerFn);
            }))
            .doOnNext(token -> {
                String preview = token.length() > 15
                    ? token.substring(0, 15) + "..."
                    : token;
                log.info("[InternalTokenCache] return token for key={}, len={}, preview={}",
                    key, token.length(), preview);
            });
    }

    // ====== Cache ======
    private record Cached(String token, String jti, long exp) {}

    private Mono<Cached> getCached(String key) {
        return values.get(key)
            .flatMap(json -> {
                try {
                    return Mono.just(objectMapper.readValue(json, Cached.class));
                } catch (JsonProcessingException e) {
                    return Mono.empty();
                }
            });
    }

    private Mono<Void> setCached(String key, Cached value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return values.set(key, json, Duration.ofSeconds(ttlSeconds)).then();
        } catch (JsonProcessingException e) {
            return Mono.empty();
        }
    }

    // ====== Keying ======
    private String sourceKey(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) {
            var jwt = t.getToken();
            String jti = jwt.getId();
            String clientId = jwt.getClaimAsString("client_id");
            String azp = jwt.getClaimAsString("azp");
            String sub = jwt.getSubject();
            return Texts.firstNonBlank(jti, clientId, azp, sub, auth.getName());
        }
        Object p = auth != null ? auth.getPrincipal() : null;
        if (p instanceof OidcUser u) {
            String sid = u.getClaimAsString("sid");
            String uid = u.getClaimAsString("uid");
            String sub = u.getSubject();
            return Texts.firstNonBlank(sid, uid, sub, auth.getName());
        }
        return auth != null ? auth.getName() : "anonymous";
    }

    private String cacheKey(String aud, String need, String src) {
        return keyPrefix + ":" + aud + ":" + need + ":" + Hashes.sha256Base64Url(src);
    }

    // ====== Utils ======

    private Mono<Boolean> tryLock(String lockKey, long millis) {
        return values.setIfAbsent(lockKey, "1", Duration.ofMillis(millis))
            .map(Boolean.TRUE::equals)
            .defaultIfEmpty(Boolean.FALSE);
    }

    private Mono<Void> unlock(String lockKey) {
        return redis.delete(lockKey).then();
    }

    private Mono<String> getFreshToken(String key) {
        return getCached(key)
            .flatMap(cached -> {
                if (cached == null) {
                    // 방어 코드: 혹시라도 null 들어오면 무조건 재발급
                    log.warn("[InternalTokenCache] key={} cached entry is null → reissue", key);
                    return Mono.empty();
                }
                if (cached.token == null || cached.token.isBlank()) {
                    log.warn("[InternalTokenCache] key={} cached token is null/blank → reissue", key);
                    return Mono.empty();
                }

                long now = Instant.now().getEpochSecond();
                long remaining = cached.exp - now;

                if (remaining > refreshWindowSec) {
                    log.debug("[InternalTokenCache] reuse token. key={}, remaining={}s", key, remaining);
                    return Mono.just(cached.token);
                } else {
                    log.debug("[InternalTokenCache] token near expiry. key={}, remaining={}s → reissue", key, remaining);
                    return Mono.empty();
                }
            });
    }

    private Mono<String> enforceWithLock(String key, Supplier<InternalTokenIssuer.IssuedToken> issuerFn) {
        String lockKey = key + ":lock";
        return tryLock(lockKey, 800)
            .flatMap(locked -> {
                Mono<String> pipeline = getFreshToken(key)
                    .switchIfEmpty(issueAndCache(key, issuerFn));
                if (locked) {
                    return pipeline
                        .flatMap(token -> unlock(lockKey).thenReturn(token))
                        .onErrorResume(e -> unlock(lockKey).then(Mono.error(e)));
                }
                return pipeline;
            });
    }

    private Mono<String> issueAndCache(String key, Supplier<InternalTokenIssuer.IssuedToken> issuerFn) {
        return Mono.fromCallable(issuerFn::get)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(it -> {
                long ttl = Math.max(1, it.expEpochSeconds() - Instant.now().getEpochSecond());
                Cached cached = new Cached(it.token(), it.jti(), it.expEpochSeconds());
                return setCached(key, cached, ttl).thenReturn(it.token());
            });
    }
}
