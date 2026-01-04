package com.colombus.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.ConfigureReactiveRedisAction;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisIndexedWebSession;

@Configuration(proxyBeanMethods = false)
@EnableRedisIndexedWebSession(
    redisNamespace = "{authsvc:sess}"
)
public class SessionConfig {
        @Bean
    public ConfigureReactiveRedisAction configureReactiveRedisAction() {
        // Redis에 CONFIG 명령 안 날리도록 NO_OP로 막기
        // 추후 수정 필요
        return ConfigureReactiveRedisAction.NO_OP;
    }
}
