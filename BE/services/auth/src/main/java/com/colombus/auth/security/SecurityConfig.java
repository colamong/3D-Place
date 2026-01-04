package com.colombus.auth.security;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.session.ReactiveFindByIndexNameSessionRepository;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.web.server.ServerWebExchange;

import com.colombus.auth.exception.AuthErrorCode;
import com.colombus.auth.security.auth.CustomLogoutHandler;
import com.colombus.auth.security.auth.JsonLogoutSuccessHandler;
import com.colombus.auth.security.auth.LoginSuccessEnsureListener;
import com.colombus.auth.security.auth.LogoutLoggingHandler;
import com.colombus.auth.security.handler.denied.CompositeAccessDeniedHandler;
import com.colombus.auth.security.handler.entrypoint.TypeDelegatingEntryPoint;
import com.colombus.auth.security.jwt.TrustedIssuersProperties;
import com.colombus.common.utility.text.Texts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableConfigurationProperties(TrustedIssuersProperties.class)
public class SecurityConfig {

    public static final String S_LOGIN_STATUS = "LOGIN_STATUS";
    public static final String S_LOGIN_REASON = "LOGIN_REASON";

    public static final String TOKEN_URI = "/internal/token";
    public static final String TOKEN_SESSION_URI = "/internal/token/session";

    @Value("${app.front.entry-url:http://localhost:5173}")
    private String frontEntryUrl;

    @Bean
    ServerAuthenticationEntryPoint authEntryPoint(ObjectMapper om) {
        return new TypeDelegatingEntryPoint(om, AuthErrorCode.INVALID_SESSION)
            .register(BadCredentialsException.class, AuthErrorCode.INVALID_CREDENTIALS)
            .register(SessionAuthenticationException.class, AuthErrorCode.SESSION_EXPIRED)
            .register(InsufficientAuthenticationException.class, AuthErrorCode.INVALID_SESSION);
    }

    @Bean
    ServerAccessDeniedHandler accessDeniedDelegator(ObjectMapper om) {
        return new CompositeAccessDeniedHandler(om);
    }

    @Bean @Order(1)
    SecurityWebFilterChain mainSecurityFilterChain(
        ServerHttpSecurity http,
        TrustedIssuersProperties trustedIssuersProps,
        ServerAuthenticationEntryPoint entryPoint,
        ServerAccessDeniedHandler deniedHandler,
        ServerCsrfTokenRepository csrfRepository,
        ServerCsrfTokenRequestHandler csrfRequestHandler,
        ServerSecurityContextRepository contextRepo,
        LoginSuccessEnsureListener loginSuccessEnsureListener,
        JsonLogoutSuccessHandler logoutSuccessHandler,
        LogoutLoggingHandler logoutLoggingHandler,
        CustomLogoutHandler customLogoutHandler
    ) {
        // 단일 SecurityWebFilterChain에서 전역 예외/인가/로그인을 모두 다룰 수 있도록 구성한다.
        http.exceptionHandling(spec -> spec
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(deniedHandler)
        );

        // WebFlux의 CSRF는 matcher로 제어하므로, 세션 교환 경로만 제외하고 기본 정책을 유지한다.
        http.csrf(csrf -> csrf
            .csrfTokenRepository(csrfRepository)
            .csrfTokenRequestHandler(csrfRequestHandler)
            .requireCsrfProtectionMatcher(csrfProtectionMatcher())
        );

        // 경로별 인가 정책: 공개 경로 → 특수 POST → 내부 API 순으로 선언하고, 마지막 anyExchange()는 기본 인증 요구다.
        // 새 엔드포인트를 추가할 때는 여기에서 경로/메서드 조합을 명시해주면 된다.
        http.authorizeExchange(exchange -> exchange
            .pathMatchers("/", "/.well-known/**", "/auth/state", "/session-expired", "/session-invalid").permitAll()
            .pathMatchers("/oauth2/**", "/login/**").permitAll()
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .pathMatchers(HttpMethod.POST, TOKEN_URI, TOKEN_SESSION_URI).authenticated()
            .pathMatchers("/internal/auth/**", "/internal/users/**").authenticated()
            .pathMatchers("/internal/auth0/**").authenticated()
            .anyExchange().authenticated()
        );

        http.oauth2ResourceServer(oauth -> oauth
            .authenticationManagerResolver(multiIssuerResolver(trustedIssuersProps))
        );

        // OAuth2 로그인 성공/실패시 Reactor 체인을 통해 세션/CSRF/리다이렉트 순서를 보장한다.
        http.oauth2Login(oauth -> oauth
            .authenticationSuccessHandler(oauthSuccessHandler(loginSuccessEnsureListener, csrfRepository, contextRepo))
            .authenticationFailureHandler(oauthFailureHandler(csrfRepository))
        );

        http.logout(logout -> logout
            .logoutUrl("/logout")
            .requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/logout"))
            .logoutHandler(logoutLoggingHandler)
            .logoutHandler(customLogoutHandler)
            .logoutSuccessHandler(logoutSuccessHandler)
        );

        return http.build();
    }

    @Bean
    ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    private ServerAuthenticationSuccessHandler oauthSuccessHandler(
        LoginSuccessEnsureListener loginSuccessEnsureListener,
        ServerCsrfTokenRepository csrfRepository,
        ServerSecurityContextRepository contextRepo
    ) {
        return (webFilterExchange, authentication) -> {
            var exchange = webFilterExchange.getExchange();
            return loginSuccessEnsureListener.ensure(exchange, authentication)
                .then(exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().put(ReactiveFindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, authentication.getName());
                    session.getAttributes().remove(S_LOGIN_STATUS);
                    session.getAttributes().remove(S_LOGIN_REASON);
                }))
                .then(Mono.defer(() -> {
                    var ctx = new SecurityContextImpl(authentication);
                    return contextRepo.save(exchange, ctx);
                }))
                .then(csrfRepository.generateToken(exchange)
                    .flatMap(token -> csrfRepository.saveToken(exchange, token)))
                .then(redirectToFront(exchange));
        };
    }

    private ServerAuthenticationFailureHandler oauthFailureHandler(ServerCsrfTokenRepository csrfRepository) {
        return (webFilterExchange, exception) -> {
            var exchange = webFilterExchange.getExchange();
            String desc = extractErrorDescription(exception);
            return exchange.getSession()
                .doOnNext(session -> {
                    if (desc.toLowerCase().contains("verify your email")) {
                        session.getAttributes().put(S_LOGIN_STATUS, "VERIFY_EMAIL_REQUIRED");
                    } else {
                        session.getAttributes().put(S_LOGIN_STATUS, "FAILED");
                    }
                    session.getAttributes().put(S_LOGIN_REASON, desc);
                })
                .then(csrfRepository.generateToken(exchange)
                    .flatMap(token -> csrfRepository.saveToken(exchange, token)))
                .then(redirectToFront(exchange));
        };
    }

    private Mono<Void> redirectToFront(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(frontEntryUrl));
        return exchange.getResponse().setComplete();
    }

    private static String extractErrorDescription(Exception ex) {
        if (ex instanceof OAuth2AuthenticationException oae && oae.getError() != null) {
            return oae.getError().getDescription() != null
                ? oae.getError().getDescription()
                : oae.getError().getErrorCode();
        }
        return ex.getMessage() != null ? ex.getMessage() : "login_failed";
    }

    // 기존 MVC에서는 체인을 분리했지만 WebFlux에서는 matcher로 예외 케이스만 분리한다.
    private ServerWebExchangeMatcher conditionalSessionExchangeIgnore() {
        return exchange -> {
            if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            String path = normalizePath(exchange.getRequest());
            if (!TOKEN_SESSION_URI.equals(path)) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            String internal = exchange.getRequest().getHeaders().getFirst("X-Internal-Exchange");
            String required = exchange.getRequest().getHeaders().getFirst("X-CSRF-REQUIRED");
            boolean ignore = "1".equals(internal) && "0".equals(required);

            String csrfHeader = exchange.getRequest().getHeaders().getFirst("X-CSRF-TOKEN");
            String csrfCookie = exchange.getRequest().getCookies().getFirst("XSRF-TOKEN") != null 
                ? exchange.getRequest().getCookies().getFirst("XSRF-TOKEN").getValue() : "MISSING";

            log.info("[CSRF-Matcher] POST {} check. Internal={}, Required={}, Ignore={}. Header={}, Cookie={}",
                path, internal, required, ignore, 
                csrfHeader != null ? csrfHeader.substring(0, Math.min(10, csrfHeader.length())) + "..." : "MISSING",
                csrfCookie.substring(0, Math.min(10, csrfCookie.length())) + "...");

            if (ignore) {
                log.debug("[SecurityConfig] Skipping CSRF for internal session exchange, X-CSRF-REQUIRED=0");
                return ServerWebExchangeMatcher.MatchResult.match();
            }
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    private ServerWebExchangeMatcher conditionalServiceExchangeIgnore() {
        return exchange -> {
            if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            String path = normalizePath(exchange.getRequest());
            if (!TOKEN_URI.equals(path)) { // "/internal/token"
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            String internal = exchange.getRequest().getHeaders().getFirst("X-Internal-Exchange");
            String required = exchange.getRequest().getHeaders().getFirst("X-CSRF-REQUIRED");
            boolean ignore = "1".equals(internal) && "0".equals(required);
            if (ignore) {
                log.debug("[SecurityConfig] Skipping CSRF for internal service exchange, X-CSRF-REQUIRED=0");
                return ServerWebExchangeMatcher.MatchResult.match();
            }
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    private ServerWebExchangeMatcher conditionalInternalApiIgnore() {
        return exchange -> {
            HttpMethod method = exchange.getRequest().getMethod();
            if (method == null) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }

            // GET/HEAD/TRACE/OPTIONS 는 어차피 CSRF 대상 아님
            Set<HttpMethod> safeMethods = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.OPTIONS);
            if (safeMethods.contains(method)) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }

            String path = normalizePath(exchange.getRequest());
            // 내부 전용 API만 (원하면 "/internal/auth0/**" 만으로 좁힐 수도 있음)
            if (!path.startsWith("/internal/")) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }

            String internal = exchange.getRequest().getHeaders().getFirst("X-Internal-Exchange");
            String required = exchange.getRequest().getHeaders().getFirst("X-CSRF-REQUIRED");
            boolean ignore = "1".equals(internal) && "0".equals(required);

            if (ignore) {
                log.debug("[SecurityConfig] Skipping CSRF for internal API {}, method={}", path, method);
                return ServerWebExchangeMatcher.MatchResult.match();
            }
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    private ServerWebExchangeMatcher csrfProtectionMatcher() {
        ServerWebExchangeMatcher skipSession  = conditionalSessionExchangeIgnore();
        ServerWebExchangeMatcher skipService  = conditionalServiceExchangeIgnore();
        ServerWebExchangeMatcher skipInternal = conditionalInternalApiIgnore();
        
        return exchange -> skipSession.matches(exchange).flatMap(m1 ->
            m1.isMatch()
            ? ServerWebExchangeMatcher.MatchResult.notMatch()
            : skipService.matches(exchange).flatMap(m2 ->
                m2.isMatch()
                ? ServerWebExchangeMatcher.MatchResult.notMatch()
                : skipInternal.matches(exchange).flatMap(m3 ->
                    m3.isMatch()
                    ? ServerWebExchangeMatcher.MatchResult.notMatch()
                    : defaultCsrfMatcher().matches(exchange)
                )
            )
        );
    }

    private ServerWebExchangeMatcher defaultCsrfMatcher() {
        Set<HttpMethod> safeMethods = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.OPTIONS);
        return exchange -> {
            HttpMethod method = exchange.getRequest().getMethod();
            // String path = normalizePath(exchange.getRequest());

            // // 로그아웃은 CSRF 없이 허용
            // if (HttpMethod.POST.equals(method) && "/logout".equals(path)) {
            //     log.debug("[SecurityConfig] Skipping CSRF for logout {}", path);
            //     return ServerWebExchangeMatcher.MatchResult.notMatch();
            // }


            boolean require = method == null || !safeMethods.contains(method);
            return require
                ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    private String normalizePath(ServerHttpRequest request) {
        String ctx = request.getPath().contextPath().value();
        String path = request.getPath().value();
        if (Texts.hasText(ctx) && path.startsWith(ctx)) {
            return path.substring(ctx.length());
        }
        return path;
    }

    // Auth0 멀티 issuer 지원: 발급자별로 ReactiveJwtAuthenticationManager를 미리 구성해 둔다.
    private ReactiveAuthenticationManagerResolver<ServerWebExchange> multiIssuerResolver(TrustedIssuersProperties props) {
        Map<String, ReactiveAuthenticationManager> managers = new HashMap<>();

        for (var cfg : props.getIssuers()) {
            NimbusReactiveJwtDecoder decoder = buildDecoder(cfg);
            applyValidators(decoder, cfg);

            JwtReactiveAuthenticationManager provider = new JwtReactiveAuthenticationManager(decoder);
            provider.setJwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(grantScopesFromJwt()));
            managers.put(cfg.getIssuer(), provider);
        }

        ReactiveAuthenticationManagerResolver<String> delegate = issuer -> {
            ReactiveAuthenticationManager manager = managers.get(issuer);
            if (manager == null) {
                return Mono.error(new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "unknown issuer", null)));
            }
            return Mono.just(manager);
        };

        return new JwtIssuerReactiveAuthenticationManagerResolver(delegate);
    }

    private NimbusReactiveJwtDecoder buildDecoder(TrustedIssuersProperties.Issuer cfg) {
        try {
            if (Texts.hasText(cfg.getJwkJson())) {
                RSAKey rsa = RSAKey.parse(cfg.getJwkJson());
                return NimbusReactiveJwtDecoder
                    .withPublicKey(rsa.toRSAPublicKey())
                    .jwtProcessorCustomizer(customizer -> customizer.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                            new JOSEObjectType("at+jwt"),
                            JOSEObjectType.JWT,
                            null
                        )
                    ))
                    .build();
            }

            if (Texts.hasText(cfg.getJwkSetUri())) {
                return NimbusReactiveJwtDecoder.withJwkSetUri(cfg.getJwkSetUri())
                    .jwtProcessorCustomizer(customizer -> customizer.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(
                            new JOSEObjectType("at+jwt"),
                            JOSEObjectType.JWT,
                            null
                        )
                    ))
                    .build();
            }

            var decoder = ReactiveJwtDecoders.fromIssuerLocation(cfg.getIssuer());
            if (decoder instanceof NimbusReactiveJwtDecoder nimbus) {
                return nimbus;
            }
            throw new IllegalStateException("Unsupported decoder type for issuer " + cfg.getIssuer());
        } catch (ParseException | JOSEException e) {
            throw new IllegalStateException("Invalid JWK configuration for issuer: " + cfg.getIssuer(), e);
        }
    }

    private void applyValidators(NimbusReactiveJwtDecoder decoder, TrustedIssuersProperties.Issuer cfg) {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(cfg.getIssuer());
        OAuth2TokenValidator<Jwt> withAud = jwt -> {
            List<String> required = cfg.getAudiences();
            if (required == null || required.isEmpty()) return OAuth2TokenValidatorResult.success();
            List<String> aud = jwt.getAudience();
            boolean ok = aud != null && aud.stream().anyMatch(required::contains);
            return ok
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "bad audience", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAud));
    }

    private Converter<Jwt, AbstractAuthenticationToken> grantScopesFromJwt() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            String scopeStr = jwt.getClaimAsString("scope");
            if (Texts.hasText(scopeStr)) {
                for (String s : scopeStr.split("\\s+")) if (Texts.hasText(s))
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
            }

            List<String> scp = jwt.getClaimAsStringList("scp");
            if (scp != null) {
                scp.stream().filter(Texts::hasText)
                    .forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
            }

            List<String> perms = jwt.getClaimAsStringList("permissions");
            if (perms != null) {
                perms.stream().filter(Texts::hasText)
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + p)));
            }

            return new JwtAuthenticationToken(jwt, authorities);
        };
    }
}
