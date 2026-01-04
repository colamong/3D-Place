package com.colombus.gateway.security;

import java.util.Locale;
import java.util.Set;

import org.springframework.cloud.gateway.filter.*;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import com.colombus.common.utility.text.Texts;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthHeaderGuardFilter implements GlobalFilter, Ordered {

    public static final String REQUIRE_EXCHANGE_ATTR = "GW_REQUIRE_TOKEN_EXCHANGE";
    public static final String EXCHANGE_SOURCE_ATTR  = "GW_EXCHANGE_SOURCE";
    public static final String CSRF_REQUIRED_ATTR    = "GW_CSRF_REQUIRED";

    private static final Set<HttpMethod> SAFE_METHODS =
        Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) return chain.filter(exchange);

        final String entry  = valueOr(route, "entry", "browser").toLowerCase(Locale.ROOT);
        final String exchangeMode = valueOr(route, "exchange", "auto").toLowerCase(Locale.ROOT);
        final boolean needExchange = !"none".equals(exchangeMode);

        final String authz  = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        final String cookie = exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE);

        if ("browser".equals(entry)) {
            if (Texts.hasText(authz)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            if(needExchange) {
                exchange.getAttributes().put(REQUIRE_EXCHANGE_ATTR, Boolean.TRUE);
                exchange.getAttributes().put(EXCHANGE_SOURCE_ATTR, "cookie");

                boolean csrfRequired = determineCsrfRequired(exchange, route);
                exchange.getAttributes().put(CSRF_REQUIRED_ATTR, csrfRequired);
            }
            
            return chain.filter(exchange);
        }

        if ("m2m".equals(entry)) {
            if (Texts.hasText(cookie)) {
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
            if (!Texts.hasText(authz) || !authz.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            exchange.getAttributes().put(REQUIRE_EXCHANGE_ATTR, Boolean.TRUE);
            exchange.getAttributes().put(EXCHANGE_SOURCE_ATTR, "authz");
            exchange.getAttributes().put(CSRF_REQUIRED_ATTR, Boolean.FALSE);
            return chain.filter(exchange);
        }

        return chain.filter(exchange);
    }

    private boolean determineCsrfRequired(ServerWebExchange exchange, Route route) {
        HttpMethod method = exchange.getRequest().getMethod();
        String mode = valueOr(route, "csrf-mode", "default").toLowerCase(Locale.ROOT);

        if ("none".equals(mode)) {
            // 안전한 엔드포인트: 메서드와 상관없이 CSRF 안 함
            return false;
        }
        if ("always".equals(mode)) {
            // 엔드포인트 자체가 민감: 모든 메서드에 CSRF 요구
            return true;
        }

        // default: unsafe 메서드에만 CSRF (GET/HEAD/OPTIONS 제외)
        if (method == null) return true; // 보수적으로
        return !SAFE_METHODS.contains(method);
    }

    private static String meta(Route r, String key) {
        Object v = r.getMetadata() != null ? r.getMetadata().get(key) : null;
        return v != null ? String.valueOf(v) : null;
    }
    
    private static String valueOr(Route r, String key, String def) {
        String v = meta(r, key);
        return (v != null && !v.isBlank()) ? v : def;
    }
}