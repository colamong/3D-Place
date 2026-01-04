package com.colombus.auth.security.auth;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;

import com.colombus.auth.infra.usersvc.UserSvcClient;
import com.colombus.auth.web.CookieUtils;

@Component
@RequiredArgsConstructor
public class CustomLogoutHandler implements ServerLogoutHandler {

    private final UserSvcClient userSvcClient;

    private static String clientIp(ServerHttpRequest req) {
        String ip = req.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        } else if (req.getRemoteAddress() != null && req.getRemoteAddress().getAddress() != null) {
            ip = req.getRemoteAddress().getAddress().getHostAddress();
        } else {
            ip = "0.0.0.0";
        }
        return ip;
    }

    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        var serverExchange = exchange.getExchange();
        ServerHttpRequest request = serverExchange.getRequest();
        ServerHttpResponse response = serverExchange.getResponse();

        // 사용자 로그아웃 이벤트는 외부 user-svc로 비동기 전송한다(실패해도 세션 정리는 계속 진행).
        OidcUser user = authentication != null && authentication.getPrincipal() instanceof OidcUser u ? u : null;
        String ua = request.getHeaders().getFirst("User-Agent");
        String issuer = user != null && user.getIssuer() != null ? user.getIssuer().toString() : null;

        Mono<Void> recordLogout = userSvcClient
            .recordLogout(authentication, user, issuer, clientIp(request), ua)
            .onErrorResume(e -> Mono.empty());

        // WebSession은 reactive invalidate로 갱신해야 하므로 Mono 체인으로 묶는다.
        Mono<Void> invalidateSession = serverExchange.getSession()
            .flatMap(session -> session.invalidate().onErrorResume(e -> Mono.empty()))
            .onErrorResume(e -> Mono.empty());

        // 쿠키 파기는 서버 응답 헤더만 만지면 되므로 fire-and-forget 형태로 처리한다.
        Mono<Void> expireCookies = Mono.fromRunnable(() ->
                CookieUtils.expireSessionCookies(request, response, "AUTHSESSION", "JSESSIONID")
            ).then();

        return Mono.when(recordLogout, invalidateSession, expireCookies);
    }
}
