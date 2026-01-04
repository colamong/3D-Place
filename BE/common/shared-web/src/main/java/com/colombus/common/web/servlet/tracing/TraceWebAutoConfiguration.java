package com.colombus.common.web.servlet.tracing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.colombus.common.web.core.tracing.TraceIdProvider;
import com.colombus.common.web.core.tracing.TracingProps;

import io.micrometer.tracing.Tracer;

@AutoConfiguration
@EnableConfigurationProperties(TracingProps.class)
public class TraceWebAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = { "jakarta.servlet.Servlet", "jakarta.servlet.Filter" })
    static class ServletConfig {

        @Bean
        FilterRegistrationBean<RequestTracingFilter> requestTracingFilter(TracingProps props) {
            var reg = new FilterRegistrationBean<>(new RequestTracingFilter(props));
            reg.setOrder(Integer.MIN_VALUE + 100);
            return reg;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = { "org.springframework.web.server.WebFilter" })
    static class ReactiveConfig {

        @Bean
        org.springframework.web.server.WebFilter traceWebFilter(TracingProps props) {
            return new TraceWebFilter(props);
        }
    }

    @Bean
    TraceIdProvider traceIdProvider(ObjectProvider<Tracer> tracerProvider) {
        return new TraceIdProvider(tracerProvider.getIfAvailable());
    }

}