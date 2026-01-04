package com.colombus.auth.security.csrf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

@Configuration
public class CsrfConfig {

    @Bean
    public ServerCsrfTokenRepository csrfCookieRepository(
        @Value("${app.security.csrf.http-only:false}") boolean httpOnly,
        @Value("${server.servlet.context-path:}") String ctxPath
    ) {
        CookieServerCsrfTokenRepository repo = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        if (httpOnly) {
            repo = new CookieServerCsrfTokenRepository();
        }
        String path = (ctxPath == null || ctxPath.isBlank()) ? "/" : ctxPath;
        repo.setCookieCustomizer(c -> c
                .path(path)
                .secure(true)
                .sameSite("Lax")
        );
        return repo;
    }

    @Bean
    public ServerCsrfTokenRequestHandler csrfRequestHandler() {
        return new SpaServerCsrfTokenRequestHandler();
    }

    @Bean
    public WebFilter csrfHeaderExposer(ServerCsrfTokenRepository repo) {
        // SPA가 최초 요청에서 CSRF 토큰을 몰라도 되도록, 매 요청마다 헤더에 최신 토큰을 실어준다.
        return (exchange, chain) -> repo.loadToken(exchange)
            .switchIfEmpty(Mono.defer(() -> repo.generateToken(exchange)
                .flatMap(token -> repo.saveToken(exchange, token).thenReturn(token))))
            .doOnNext(token -> setHeader(exchange.getResponse(), token))
            .then(chain.filter(exchange));
    }

    private static void setHeader(ServerHttpResponse response, CsrfToken token) {
        if (token != null) {
            response.getHeaders().set(token.getHeaderName(), token.getToken());
        }
    }

    static final class SpaServerCsrfTokenRequestHandler implements ServerCsrfTokenRequestHandler {
        private final ServerCsrfTokenRequestAttributeHandler plain = new ServerCsrfTokenRequestAttributeHandler();
        private final XorServerCsrfTokenRequestAttributeHandler xor = new XorServerCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(org.springframework.web.server.ServerWebExchange exchange,
                           reactor.core.publisher.Mono<CsrfToken> deferredCsrfToken) {
            xor.handle(exchange, deferredCsrfToken);
            deferredCsrfToken.subscribe();
        }

        @Override
        public reactor.core.publisher.Mono<String> resolveCsrfTokenValue(
            org.springframework.web.server.ServerWebExchange exchange,
            CsrfToken csrfToken
        ) {
            String header = exchange.getRequest().getHeaders().getFirst(csrfToken.getHeaderName());
            if (header != null && !header.isBlank()) {
                return plain.resolveCsrfTokenValue(exchange, csrfToken);
            }
            return xor.resolveCsrfTokenValue(exchange, csrfToken);
        }
    }
}
