package com.colombus.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RequestLoggingConfig {

    @Bean
    @Order(-100)
    public WebFilter bffRequestLoggingFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            var req = exchange.getRequest();

            String authz = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String preview = authz == null
                ? "null"
                : (authz.length() > 25 ? authz.substring(0, 25) + "..." : authz);

            log.info("[BFF-REQ] {} {} Authorization={}",
                req.getMethod(),
                req.getPath().value(),
                preview
            );

            return chain.filter(exchange);
        };
    }
}