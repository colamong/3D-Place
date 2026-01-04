package com.colombus.common.web.webflux.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = Type.REACTIVE)
@ConditionalOnClass(WebFluxConfigurer.class)
@ConditionalOnProperty(name = "colombus.exception.webflux-advice.enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveExceptionAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BaseReactiveExceptionHandler.class)
    public GlobalReactiveExceptionHandler globalReactiveExceptionHandler() {
        return new GlobalReactiveExceptionHandler();
    }
}