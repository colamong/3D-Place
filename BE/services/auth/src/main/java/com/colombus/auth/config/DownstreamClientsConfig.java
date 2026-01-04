package com.colombus.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.colombus.auth.infra.auth0.Auth0Properties;

@Configuration
public class DownstreamClientsConfig {

    @Value("${service.user.base-url}")
    private String userSvcBaseUrl;

    @Bean
    public WebClient userClient(WebClient.Builder builder) {
        return builder
            .baseUrl(userSvcBaseUrl + "/internal/users")
            .build();
    }

    @Bean
    public WebClient auth0Client(
        WebClient.Builder builder,
        Auth0Properties auth0Properties
    ) {
        return builder
            .baseUrl("https://" + auth0Properties.domain())
            .build();
    }

    @Bean
    public WebClient oidcClient(WebClient.Builder builder) {
        return builder.build();
    }
}
