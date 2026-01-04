package com.colombus.bff.security;

import java.security.PrivateKey;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.colombus.common.security.assertion.ClientAssertionProperties;
import com.colombus.common.security.assertion.ClientAssertionSigner;

@Configuration
public class ClientAssertionConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "service.assertion")
    public ClientAssertionProperties clientAssertionProperties() {
        return new ClientAssertionProperties();
    }

    @Bean
    public ClientAssertionSigner clientAssertionSigner(ClientAssertionProperties props, Clock clock) {
        PrivateKey pk = ClientAssertionSigner.loadPrivateKey(props);
        return new ClientAssertionSigner(pk, props, clock);
    }
}
