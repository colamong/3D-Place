package com.colombus.auth.config;

import com.colombus.auth.infra.session.LinkSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean("linkSessionReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, LinkSession> linkSessionReactiveRedisTemplate(
            ReactiveRedisConnectionFactory cf, ObjectMapper om) {
        var keySer = new StringRedisSerializer();
        var valueSer = new Jackson2JsonRedisSerializer<>(om, LinkSession.class);

        RedisSerializationContext<String, LinkSession> context =
            RedisSerializationContext.<String, LinkSession>newSerializationContext(keySer)
                .value(valueSer)
                .hashKey(keySer)
                .hashValue(valueSer)
                .build();

        return new ReactiveRedisTemplate<>(cf, context);
    }
}
