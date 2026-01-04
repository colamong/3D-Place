package com.colombus.common.web.servlet.clientip;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.colombus.common.web.core.clientip.TrustedProxyProperties;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class ClientIpMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClientIpService clientIpService(TrustedProxyProperties props) {
        return new ClientIpService(props);
    }

    @Bean
    public WebMvcConfigurer clientIpWebMvcConfigurer(ClientIpService ipService) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new ClientIpServletArgumentResolver(ipService));
            }
        };
    }
}