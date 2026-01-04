package com.colombus.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.colombus.common.web.webflux.clientip.ReactiveClientIpService;


@Configuration
public class RateLimiterConfig {

    private final ReactiveClientIpService clientIp;

    public RateLimiterConfig(ReactiveClientIpService clientIp) {
        this.clientIp = clientIp;
    }

    @Bean("userKeyResolver")
    @Primary
    public KeyResolver userKeyResolver() {
        return new UserKeyResolver(clientIp);
    }

    @Bean("m2mClientKeyResolver")
    public KeyResolver m2mClientKeyResolver() {
        return new M2MClientKeyResolver(clientIp);
    }
}