package com.colombus.user.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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
    RestClient authTokenClient(ClientAssertionSigner signer) {
        log.info("[AuthTokenClientConfig] Creating authTokenClient with baseUrl={}", authSvcBaseUrl);

        return RestClient.builder()
            .baseUrl(authSvcBaseUrl + "/internal/token")
            .requestInterceptor((req, body, ex) -> {
                // 게이트웨이 내부 호출 증표
                req.getHeaders().set("X-Internal-Exchange", "1");
                req.getHeaders().set("X-Service-Name", "user-service");
                req.getHeaders().set("X-Trace-Id", trace.currentTraceId());

                req.getHeaders().set("X-CSRF-REQUIRED", "0");

                String assertion = signer.signAssertion(180);
                req.getHeaders().setBearerAuth(assertion);

                log.info("[AuthTokenClientConfig] Requesting STS token from {}", req.getURI());
                log.debug("[AuthTokenClientConfig] Added service assertion (len={})", assertion.length());

                return ex.execute(req, body);
            })
            .build();
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
