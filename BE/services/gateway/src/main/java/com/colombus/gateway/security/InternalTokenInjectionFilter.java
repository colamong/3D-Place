package com.colombus.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class InternalTokenInjectionFilter implements GlobalFilter, Ordered {

    private final WebClient authClient;
    private final ReactiveJwtDecoder internalDecoder; 

    public InternalTokenInjectionFilter(
        WebClient.Builder builder,
        @Value("${auth-svc.base-url}") String authBaseUrl,
        @Value("${internal-jwt.jwk-set-uri}") String jwkSetUri
    ) {
        this.authClient = builder.baseUrl(authBaseUrl).build();
        NimbusReactiveJwtDecoder nrd = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> withIssuer =
            JwtValidators.createDefaultWithIssuer("http://auth-svc.internal");
        nrd.setJwtValidator(withIssuer);

        this.internalDecoder = nrd;
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 1; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        HttpMethod method = exchange.getRequest().getMethod();
        String originalPath = exchange.getRequest().getURI().getPath();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "NO_ROUTE";

        if (method == HttpMethod.OPTIONS) {
            log.debug("[InternalTokenInjectionFilter] Skip OPTIONS request. routeId={}, path={}",
                routeId, originalPath);
            return chain.filter(exchange);
        }

        Boolean requireExchange = exchange.getAttribute(AuthHeaderGuardFilter.REQUIRE_EXCHANGE_ATTR);
        String source = exchange.getAttribute(AuthHeaderGuardFilter.EXCHANGE_SOURCE_ATTR);
        Boolean csrfRequired = exchange.getAttribute(AuthHeaderGuardFilter.CSRF_REQUIRED_ATTR);
        boolean needCsrf = Boolean.TRUE.equals(csrfRequired);

        log.info("[InternalTokenInjectionFilter] requireExchange={}, source={}, csrfRequired={}, needCsrf={}",
            requireExchange, source, csrfRequired, needCsrf);

        if (requireExchange == null || !requireExchange) {
            log.debug("[InternalTokenInjectionFilter] requireExchange=false → skip token exchange. routeId={}, path={}",
                routeId, originalPath);
            return chain.filter(exchange);
        }

        if (route == null) {
            log.warn("[InternalTokenInjectionFilter] GATEWAY_ROUTE_ATTR is null. Skip token exchange. method={}, path={}",
                method, originalPath);
            return chain.filter(exchange);
        }

        String internalPath = "cookie".equals(source) ? "/internal/token/session" : "/internal/token";

        WebClient.RequestHeadersSpec<?> req = authClient.post()
            .uri(u -> u.path(internalPath).build())
            .headers(h -> {
                h.add("X-Internal-Exchange", "1");
                h.add("X-Service-Name", "gateway-service");
                h.add("X-CSRF-REQUIRED", needCsrf ? "1" : "0");
            });

        if ("cookie".equals(source)) {
            var sess = exchange.getRequest().getCookies().getFirst("AUTHSESSION");
            if (sess == null) {
                sess = exchange.getRequest().getCookies().getFirst("JSESSIONID");
            }

            if (sess == null) {
                log.warn("[InternalTokenInjectionFilter] Session cookie missing (AUTHSESSION/JSESSIONID). routeId={}, path={}",
                    routeId, originalPath);
                return complete(exchange, HttpStatus.UNAUTHORIZED);
            }

            String sessPreview = abbrev(sess.getValue(), 12);

            if (needCsrf) {
                // CSRF 요구: XSRF 쿠키 + 헤더 둘 다 필요
                var xsrf = exchange.getRequest().getCookies().getFirst("XSRF-TOKEN");
                if (xsrf == null) {
                    log.warn("[InternalTokenInjectionFilter] UNAUTH: XSRF-TOKEN cookie missing (CSRF required). routeId={}, path={}",
                        routeId, originalPath);
                    return complete(exchange, HttpStatus.UNAUTHORIZED);
                }

                String xsrfHeader = exchange.getRequest().getHeaders().getFirst("X-XSRF-TOKEN");
                if (xsrfHeader == null || xsrfHeader.isBlank()) {
                    log.warn("[InternalTokenInjectionFilter] FORBIDDEN: X-XSRF-TOKEN header missing/blank. routeId={}, path={}, xsrfCookie={}",
                        routeId, originalPath, abbrev(xsrf.getValue(), 12));
                    return complete(exchange, HttpStatus.FORBIDDEN);
                }

                log.info("[InternalTokenInjectionFilter] CSRF Check OK. routeId={}, path={}, session={}, xsrfCookie={}, xsrfHeader={}",
                    routeId, originalPath,
                    sessPreview,
                    abbrev(xsrf.getValue(), 12),
                    abbrev(xsrfHeader, 12));

                String cookieLine = "AUTHSESSION=" + sess.getValue() + "; XSRF-TOKEN=" + xsrf.getValue();
                req = req.headers(h -> {
                    h.set(HttpHeaders.COOKIE, cookieLine);
                    h.set("X-XSRF-TOKEN", xsrfHeader);
                });
            } else {
                // 안전한 메서드 or 안전한 엔드포인트: CSRF 헤더 없이 세션만 전달
                log.info("[InternalTokenInjectionFilter] Using session cookie only (no CSRF required). routeId={}, path={}, session={}",
                    routeId, originalPath, sessPreview);
                String cookieLine = "AUTHSESSION=" + sess.getValue();
                req = req.headers(h -> h.set(HttpHeaders.COOKIE, cookieLine));
            }

        } else if ("authz".equals(source)) {
            String authz = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authz == null || !authz.startsWith("Bearer ")) {
                log.warn("[InternalTokenInjectionFilter] Missing/invalid Authorization header for authz source. routeId={}, path={}",
                    routeId, originalPath);
                return complete(exchange, HttpStatus.UNAUTHORIZED);
            }
            log.info("[InternalTokenInjectionFilter] Forwarding Authorization header (authz source). routeId={}, path={}, headerPreview={}",
                routeId, originalPath, abbrev(authz, 20));
            req = req.headers(h -> h.set(HttpHeaders.AUTHORIZATION, authz));
        } else {
            // 알 수 없는 소스
            log.warn("[InternalTokenInjectionFilter] Unknown exchange source='{}' → forbidden. routeId={}, path={}",
                source, routeId, originalPath);
            return complete(exchange, HttpStatus.FORBIDDEN);
        }

        log.info("[InternalTokenInjectionFilter] Call AuthSvc: POST {} (routeId={}, path={}, source={}, needCsrf={})",
            internalPath, routeId, originalPath, source, needCsrf);

        return req.retrieve()
            .bodyToMono(TokenRes.class)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("[InternalTokenInjectionFilter] AuthSvc returned EMPTY body. routeId={}, path={}, internalPath={}",
                    routeId, originalPath, internalPath);
                return Mono.error(new IllegalStateException("Empty response from auth service"));
            }))
            .doOnNext(tr -> {
                String preview = tr.token() != null ? abbrev(tr.token(), 15) : "null";
                int len = tr.token() != null ? tr.token().length() : -1;
                log.info("[InternalTokenInjectionFilter] Received TokenRes from AuthSvc. routeId={}, path={}, len={}, preview={}",
                    routeId, originalPath, len, preview);
            })
            .flatMap(tr ->
                internalDecoder.decode(tr.token())
                    .doOnNext(jwt -> {
                        long now = System.currentTimeMillis() / 1000;
                        long exp = jwt.getExpiresAt() != null ? jwt.getExpiresAt().getEpochSecond() : -1;
                        log.info("[InternalTokenInjectionFilter] Decoded internal JWT. routeId={}, path={}, iss={}, aud={}, sub={}, exp={}, now={}",
                            routeId, originalPath,
                            jwt.getIssuer(),
                            jwt.getAudience(),
                            jwt.getSubject(),
                            exp, now);
                    })
                    .map(jwt -> new DecodedInternal(tr.token(), jwt))
            )
            .flatMap(di -> {
                String tokenPreview = abbrev(di.token(), 15);
                log.info("[InternalTokenInjectionFilter] Inject internal token to downstream. routeId={}, path={}, tokenLen={}, preview={}",
                    routeId, originalPath, di.token().length(), tokenPreview);

                var mutated = exchange.mutate()
                    .request(r -> r.headers(h -> {
                        h.setBearerAuth(di.token());
                        h.remove(HttpHeaders.COOKIE);
                    }))
                    .build();
                return chain.filter(mutated);
            })
            .onErrorResume(WebClientResponseException.Unauthorized.class, e -> {
                log.warn("[InternalTokenInjectionFilter] Unauthorized token exchange - routeId={}, path={}, internalPath={}, source={}, status={}, body={}",
                    routeId, originalPath, internalPath, source, e.getStatusCode(), safeBody(e));
                return complete(exchange, HttpStatus.UNAUTHORIZED);
            })
            .onErrorResume(WebClientResponseException.Forbidden.class, e -> {
                log.warn("[InternalTokenInjectionFilter] Forbidden token exchange - routeId={}, path={}, internalPath={}, source={}, status={}, body={}",
                    routeId, originalPath, internalPath, source, e.getStatusCode(), safeBody(e));
                return complete(exchange, HttpStatus.FORBIDDEN);
            })
            .onErrorResume(WebClientResponseException.class, e -> {
                log.warn("[InternalTokenInjectionFilter] WebClient error during token exchange - routeId={}, path={}, internalPath={}, source={}, status={}, body={}",
                    routeId, originalPath, internalPath, source, e.getStatusCode(), safeBody(e));

                if (e.getStatusCode().is4xxClientError()) {
                    return complete(exchange, e.getStatusCode());
                }
                return complete(exchange, HttpStatus.BAD_GATEWAY);
            })
            .onErrorResume(Exception.class, e -> {
                log.warn("[InternalTokenInjectionFilter] Unexpected error during token exchange - routeId={}, path={}, internalPath={}, source={}, errorType={}, message={}",
                    routeId, originalPath, internalPath, source,
                    e.getClass().getSimpleName(), e.getMessage(), e);
                return complete(exchange, HttpStatus.UNAUTHORIZED);
            });
    }

    private static Mono<Void> complete(ServerWebExchange exchange, HttpStatusCode status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private static String abbrev(String value, int max) {
        if (value == null) return "null";
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }

    private static String safeBody(WebClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString();
            return abbrev(body, 200);
        } catch (Exception ex) {
            return "N/A";
        }
    }

    private record DecodedInternal(String token, Jwt jwt) {}

    private record TokenRes(String token) {}
}