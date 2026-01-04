package com.colombus.bff.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean @Order(1)
    @Profile("local")
    public SecurityWebFilterChain internalPermitAllChain(ServerHttpSecurity http) {
        http.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth
                .anyExchange().permitAll()
            );

        return http.build();
    }

    @Bean @Order(1)
    @Profile("!local")
    public SecurityWebFilterChain internalSecureChain(
        ServerHttpSecurity http,
        ReactiveJwtDecoder jwtDecoder,
        Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter
    ) {
        log.info("[BFF-Security] building /api/** secure chain (prod). decoder={}, converter={}",
            jwtDecoder.getClass().getSimpleName(),
            jwtAuthConverter.getClass().getSimpleName()
        );

        http.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth
                .pathMatchers(
                    "/api/users/profile/*",
                    "/api/clans/public",
                    "/api/clans/*/detail",
                    "/api/leaderboard",
                    "/api/world/**"
                ).permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth.jwt(j -> j.jwtDecoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter)));

        return http.build();
    }
}
