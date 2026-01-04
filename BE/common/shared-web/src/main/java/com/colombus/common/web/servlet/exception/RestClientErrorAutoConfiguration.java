package com.colombus.common.web.servlet.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;


@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass({RestClient.class, ObjectMapper.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
public class RestClientErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestClientErrorHandler restClientErrorHandler(ObjectMapper objectMapper) {
        return new RestClientErrorHandler(objectMapper);
    }
    
    @Bean
    public RestClientCustomizer restClientErrorCustomizer(RestClientErrorHandler handler) {
        return builder -> handler.configure(builder);
    }
}
