package com.colombus.common.web.webflux.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfiguration(after = WebClientAutoConfiguration.class)
@ConditionalOnClass({WebClient.class, ObjectMapper.class})
@ConditionalOnWebApplication(type = Type.REACTIVE)
public class WebClientErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClientErrorHandler webClientErrorHandler(ObjectMapper objectMapper) {
        return new WebClientErrorHandler(objectMapper);
    }

    @Bean
    public WebClientCustomizer webClientErrorCustomizer(WebClientErrorHandler handler) {
        return builder -> handler.configure(builder);
    }
    
}
