package com.colombus.user.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBroker {
    
    private final RestClient authTokenClient;
    private final Clock clock;

    private static final class Entry {
        final String token;
        final Instant expAt;
        Entry(String token, Instant expAt) { this.token = token; this.expAt = expAt; }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public String getToken(String audience, long ttlSec) {
        var now = clock.instant();
        var e = cache.get(audience);
        if (e != null && e.expAt.isAfter(now.plusSeconds(5))) {
            log.debug("[TokenBroker] Using cached token for audience={}", audience);
            return e.token;
        }

        log.info("[TokenBroker] Requesting new token - now={}, audience={}, ttl={}", now, audience, ttlSec);

        try {
            var resp = authTokenClient.post()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("aud", audience)
                    .queryParam("ttl", ttlSec)
                    .build())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TokenResponse.class);

            log.info("[TokenBroker] STS response received - resp={}", resp);
            
            if (resp == null || resp.token == null || resp.token.isBlank()) {
                throw new IllegalStateException("STS mint failed: empty token for aud=" + audience);
            }

            // 로컬 캐시 만료(버퍼 10s)
            var newExp = now.plusSeconds(Math.max(10, ttlSec - 10));
            cache.put(audience, new Entry(resp.token, newExp));
            
            log.info("[TokenBroker] Token cached successfully - audience={}, expires_at={}", audience, newExp);
            return resp.token;
            
        } catch (RestClientException ex) {
            log.error("[TokenBroker] Failed to get token from auth-service - audience={}, error={}", 
                audience, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to exchange token for aud=" + audience, ex);
        }
    }

    public void invalidate(String audience) {
        cache.remove(audience);
    }

    public static record TokenResponse(String token) {}
}
