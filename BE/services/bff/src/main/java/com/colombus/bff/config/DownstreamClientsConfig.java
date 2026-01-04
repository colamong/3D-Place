package com.colombus.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import com.colombus.bff.security.TokenBroker;
import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.common.web.webflux.exception.WebClientErrorHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DownstreamClientsConfig {

    private final TraceIdProvider trace;
    private final TokenBroker tokenBroker;
    private final WebClient.Builder webClientBuilder;
    private final WebClientErrorHandler errorHandler;

    private WebClient.Builder createDownstreamBuilder() {
        ExchangeFilterFunction tokenFilter = (request, next) -> {
            String url = request.url().toString();
            String aud = resolveAudience(url);

            log.info("[DownstreamInterceptor] Before token exchange - url={}, aud={}", url, aud);

            return tokenBroker.getToken(aud, 120)
                .map(sts -> {
                    log.info("[DownstreamInterceptor] After token exchange - aud={}, token_length={}", 
                        aud, sts != null ? sts.length() : 0);
                    return ClientRequest.from(request)
                        .headers(h -> {
                            h.setBearerAuth(sts);
                            h.set("X-Trace-Id", trace.currentTraceId());
                            h.set("X-Internal-Exchange", "1");
                            h.set("X-Service-Name", "bff-service");
                        })
                        .build();
                })
                .flatMap(next::exchange);
        };

        return errorHandler.configure(webClientBuilder.clone())
            .filter(tokenFilter);
    }

    private static String resolveAudience(String fullUrl) {
        var p = fullUrl.toLowerCase();
        if (p.contains("/internal/clans"))  return "clan-svc";
        if (p.contains("/internal/users"))  return "user-svc";
        if (p.contains("/internal/uploads")) return "media-svc";
        if (p.contains("/internal/paints")) return "paint-svc";
        if (p.contains("/internal/world"))  return "world-svc";
        if (p.contains("/internal/leaderboard")) return "leaderboard-svc";
        return "unknown";
    }

    // 요청이 필요한 클라이언트 추가부
    @Value("${service.clan.base-url}")
    private String clanSvcBaseUrl;

    @Value("${service.user.base-url}")
    private String userSvcBaseUrl;

    @Value("${service.auth.base-url}")
    private String authSvcBaseUrl;

    @Value("${service.media.base-url}")
    private String mediaSvcBaseUrl;

    @Value("${service.paint.base-url}")
    private String paintSvcBaseUrl;

    @Value("${service.world.base-url}")
    private String worldSvcBaseUrl;

    @Value("${service.leaderboard.base-url}")
    private String leaderboardSvcBaseUrl;

    @Bean
    public WebClient clanClient() {
        return createDownstreamBuilder()
            .baseUrl(clanSvcBaseUrl + "/internal/clans")
            .build();
    }

    @Bean
    public WebClient userClient() {
        return createDownstreamBuilder()
            .baseUrl(userSvcBaseUrl + "/internal/users")
            .build();
    }

    @Bean
    public WebClient authClient() {
        return createDownstreamBuilder()
            .baseUrl(authSvcBaseUrl + "/internal/auth")
            .build();
    }

    @Bean
    public WebClient mediaClient() {
        return createDownstreamBuilder()
            .baseUrl(mediaSvcBaseUrl + "/internal/uploads")
            .build();
    }

    @Bean
    public WebClient paintClient() {
        return createDownstreamBuilder()
                .baseUrl(paintSvcBaseUrl + "/internal/paints")
                .build();
    }

    @Bean
    public WebClient worldClient() {
        return createDownstreamBuilder()
                .baseUrl(worldSvcBaseUrl + "/internal/world")
                .build();
    }

    @Bean
    public WebClient leaderboardClient() {
        return createDownstreamBuilder()
            .baseUrl(leaderboardSvcBaseUrl + "/internal/leaderboard")
            .build();
    }
}
