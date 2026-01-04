package com.colombus.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import com.colombus.common.utility.crypto.Hashes;
import com.colombus.common.web.webflux.clientip.ReactiveClientIpService;

import reactor.core.publisher.Mono;

public class UserKeyResolver implements KeyResolver {

    private final ReactiveClientIpService clientIp;

    public UserKeyResolver(ReactiveClientIpService clientIp) { 
        this.clientIp = clientIp;
    }

    private static final String AUTH_COOKIE = "AUTHSESSION";
    private static final String UID_CLAIM   = "uid"; // 내부 토큰에 있을 수 있는 커스텀 클레임

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        // 세션 쿠키 기반
        var c = exchange.getRequest().getCookies().getFirst(AUTH_COOKIE);
        if (c != null) {
            String hashed = Hashes.sha256Base64Url(c.getValue());
            return Mono.just("cookie:" + hashed);
        }

        // Bearer 기반 (sub 또는 uid)
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            var claimsOpt = JwtKeyUtils.decodeJwtPayload(auth);
            if (claimsOpt.isPresent()) {
                var claims = claimsOpt.get();
                String sub = JwtKeyUtils.str(claims.get("sub"));
                String uid = JwtKeyUtils.str(claims.get(UID_CLAIM));
                String basis = (uid != null && !uid.isBlank()) ? uid : sub;
                if (basis != null && !basis.isBlank()) {
                    return Mono.just("user:" + Hashes.sha256Base64Url(basis));
                }
            }
            // 토큰은 있으나 파싱 실패 → 토큰 전체 해시로 최후 fallback
            return Mono.just("bearer:" + Hashes.sha256Base64Url(auth));
        }

        return clientIp.resolve(exchange)
            .map(ip -> "ip:" + ip);
    }
}
