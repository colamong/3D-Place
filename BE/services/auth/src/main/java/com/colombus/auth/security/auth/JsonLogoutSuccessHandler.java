package com.colombus.auth.security.auth;

import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.security.web.server.csrf.CsrfToken;

@Component
@RequiredArgsConstructor
public class JsonLogoutSuccessHandler implements ServerLogoutSuccessHandler {

    @Value("${auth0.domain}")
    private String auth0Domain;
    @Value("${auth0.client-id}")
    private String clientId;
    @Value("${app.logout-return}")
    private String returnTo;

    private final ServerCsrfTokenRepository csrfRepo;

    private String getAuth0BaseUrl() {
        if (auth0Domain.startsWith("http://") || auth0Domain.startsWith("https://")) {
            return auth0Domain;
        }
        return "https://" + auth0Domain;
    }

    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
        var serverExchange = exchange.getExchange();
        // WebFlux 로그아웃에서는 CSRF 토큰을 즉시 재발급해 프론트가 다음 요청에 재사용할 수 있도록 한다.
        return csrfRepo.generateToken(serverExchange)
            .flatMap(token -> csrfRepo.saveToken(serverExchange, token).thenReturn(token))
            .flatMap(token -> writeResponse(serverExchange.getResponse(), token));
    }

    private Mono<Void> writeResponse(org.springframework.http.server.reactive.ServerHttpResponse response, CsrfToken token) {
        response.setStatusCode(org.springframework.http.HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(token.getHeaderName(), token.getToken());
        response.getHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.getHeaders().set("Pragma", "no-cache");

        String baseUrl = getAuth0BaseUrl();

        String url = UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment("v2", "logout")
            .queryParam("client_id", clientId)
            .queryParam("returnTo", returnTo)
            .build(true)
            .toUriString(); 

        String body = "{\"frontChannelLogout\":\"" + url + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
