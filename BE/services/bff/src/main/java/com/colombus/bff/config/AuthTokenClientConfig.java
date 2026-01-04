package com.colombus.bff.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.colombus.common.security.assertion.ClientAssertionSigner;
import com.colombus.common.web.core.tracing.TraceIdProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AuthTokenClientConfig {
    
    private final TraceIdProvider trace;

    public AuthTokenClientConfig(TraceIdProvider trace) { this.trace = trace; }

    @Value("${service.auth.base-url}")
    private String authSvcBaseUrl;

    @Bean
    WebClient authTokenClient(ClientAssertionSigner signer, WebClient.Builder builder) {
        log.info("[AuthTokenClientConfig] Creating authTokenClient with baseUrl={}", authSvcBaseUrl);

        ExchangeFilterFunction headerFilter = (request, next) -> {
            String assertion = signer.signAssertion(180);
            ClientRequest mutated = ClientRequest.from(request)
                .headers(h -> {
                    h.set("X-Internal-Exchange", "1");
                    h.set("X-Service-Name", "bff-service");
                    h.set("X-Trace-Id", trace.currentTraceId());
                    h.set("X-CSRF-REQUIRED", "0");
                    h.setBearerAuth(assertion);
                })
                .build();

            log.info("[AuthTokenClientConfig] Requesting STS token from {}", mutated.url());
            log.debug("[AuthTokenClientConfig] Added service assertion (len={})", assertion.length());

            return next.exchange(mutated);
        };

        return builder.clone()
            .baseUrl(authSvcBaseUrl + "/internal/token")
            .filter(headerFilter)
            .build();
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
