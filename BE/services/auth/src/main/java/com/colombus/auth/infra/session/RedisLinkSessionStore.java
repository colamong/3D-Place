package com.colombus.auth.infra.session;

import com.colombus.auth.infra.oidc.Pkce;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class RedisLinkSessionStore implements LinkSessionStore {

    private final ReactiveRedisTemplate<String, LinkSession> redis;
    private final ReactiveValueOperations<String, LinkSession> values;
    private final String keyPrefix;

    public RedisLinkSessionStore(
        @Qualifier("linkSessionReactiveRedisTemplate") ReactiveRedisTemplate<String, LinkSession> redis,
        @Value("${link.session.key-prefix:link:session:}") String keyPrefix
    ) {
        this.redis = redis;
        this.values = redis.opsForValue();
        this.keyPrefix = keyPrefix;
    }

    private String k(String state) { return keyPrefix + state; }

    @Override
    public Mono<LinkSession> issue(UUID userId, String provider, String redirectUri,
                             Duration ttl, String ipHash, String uaHash) {

        String state = Pkce.randomState();
        String verifier = Pkce.newVerifier();
        String challenge = Pkce.challengeS256(verifier);

        LinkSession s = new LinkSession(
            state, userId, provider, verifier, challenge, redirectUri,
            Instant.now().plus(ttl), ipHash, uaHash
        );

        String key = k(state);
        return values.set(key, s, ttl).thenReturn(s);
    }

    @Override
    public Mono<LinkSession> consume(String state) {
        if (state == null) return Mono.empty();

        String key = k(state);
        return values.getAndDelete(key)
            .filter(s -> s != null && !s.expired());
    }

    @Override
    public Mono<LinkSession> peek(String state) {
        if (state == null) return Mono.empty();
        return values.get(k(state))
            .filter(s -> s != null && !s.expired());
    }

    @Override
    public Mono<Void> deleteAllByUserId(UUID userId) {
        if (userId == null) return Mono.empty();
        return redis.keys(keyPrefix + "*")
            .flatMap(key -> values.get(key)
                .filter(session -> session != null && userId.equals(session.userId()))
                .flatMap(session -> values.delete(key)))
            .then();
    }
}
