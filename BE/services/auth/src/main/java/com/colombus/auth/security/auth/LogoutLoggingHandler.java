package com.colombus.auth.security.auth;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogoutLoggingHandler implements ServerLogoutHandler {

    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        var req = exchange.getExchange().getRequest();
        String requestedId = null;
        HttpCookie sessionCookie = req.getCookies().getFirst("AUTHSESSION");
        if (sessionCookie != null) {
            requestedId = sessionCookie.getValue();
        }

        final String reqId = requestedId;
        // Reactor 세션 파이프에서만 sessionId를 얻을 수 있으므로, 로그는 Mono 체인에서 남긴다.
        return exchange.getExchange().getSession()
            .map(session -> session.getId())
            .defaultIfEmpty("<none>")
            .doOnNext(currentId -> log.info("logout requestedSessionId={}, currentSessionId={}", reqId, currentId))
            .then();
    }
}
