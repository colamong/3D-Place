package com.colombus.common.web.webflux.clientip;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

import com.colombus.common.web.core.clientip.TrustedProxyProperties;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class ClientIpWebFluxAutoConfiguration {

    @Bean
    public ReactiveClientIpService reactiveClientIpService(TrustedProxyProperties props) {
        return new ReactiveClientIpService(props);
    }

    @Bean
    public WebFluxConfigurer clientIpWebFluxConfigurer(TrustedProxyProperties props) {
        return new WebFluxConfigurer() {
            @Override
            public void configureArgumentResolvers(@NonNull ArgumentResolverConfigurer c) {
                c.addCustomResolver(new ClientIpReactiveArgumentResolver(props));
            }
        };
    }
}