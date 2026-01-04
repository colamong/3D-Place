package com.colombus.world.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // 추가 체인용
    // 기본 체인은 shared-security에서 설정됨
}
