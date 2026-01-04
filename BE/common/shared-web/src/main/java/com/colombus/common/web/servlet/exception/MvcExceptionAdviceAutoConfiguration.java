package com.colombus.common.web.servlet.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(name = "colombus.exception.mvc-advice.enabled", havingValue = "true", matchIfMissing = true)
public class MvcExceptionAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BaseExceptionHandler.class)
    public GlobalMvcExceptionHandler globalMvcExceptionHandler() {
        return new GlobalMvcExceptionHandler();
    }
}