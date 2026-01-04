package com.colombus.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import com.colombus.common.utility.crypto.Hashes;
import com.colombus.common.web.webflux.clientip.ReactiveClientIpService;

import reactor.core.publisher.Mono;

/**
 * Auth0 M2M/웹훅 트래픽용 KeyResolver.
 * 우선순위: client_id → azp → iss(+aud) → "m2m:unknown"
 * - 전적으로 JWT 클레임 기반(서명 검증 없이 payload만 decode)
 * - RL 키는 신뢰판단이 아니라 '퓨즈' 용도이므로 가볍고 안정적으로.
 */
public class M2MClientKeyResolver implements KeyResolver {

    private final ReactiveClientIpService clientIp;

    public M2MClientKeyResolver(ReactiveClientIpService clientIp) { 
        this.clientIp = clientIp;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            var claimsOpt = JwtKeyUtils.decodeJwtPayload(auth);
            if (claimsOpt.isPresent()) {
                var claims = claimsOpt.get();
                String clientId = JwtKeyUtils.str(claims.get("client_id")); // Auth0에 있을 수 있음
                String azp      = JwtKeyUtils.str(claims.get("azp"));       // 일반적으로 존재
                String iss      = JwtKeyUtils.str(claims.get("iss"));
                String aud      = JwtKeyUtils.str(claims.get("aud"));

                String tenant = JwtKeyUtils.hostFromIss(iss);
                String who = (clientId != null && !clientId.isBlank())
                        ? clientId
                        : (azp != null && !azp.isBlank() ? azp : null);

                if (who != null) {
                    // 예: m2m:your-tenant.auth0.com:abc123CLIENTID
                    String key = "m2m:" + (tenant != null ? tenant : "unknown") + ":" + who;
                    return Mono.just(key);
                }

                if (tenant != null || aud != null) {
                    // 클라이언트 식별이 불가할 때 테넌트/오디언스로 묶기
                    String key = "m2m:" + (tenant != null ? tenant : "unknown") + ":" + (aud != null ? aud : "unknown");
                    return Mono.just(key);
                }
            }
            // JWT였으나 payload 파싱 실패 → 전체 토큰 해시로 최소 묶기
            return Mono.just("m2m:" + Hashes.sha256Base64Url(auth));
        }

        // Authorization 없으면 거의 비정상 케이스 → ip 키로 묶기
        return clientIp.resolve(exchange)
            .map(ip -> "m2m:ip:" + ip);
    }
}
