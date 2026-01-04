package com.colombus.common.security.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtSecurityProperties(
    String jwkSetUri,
    String issuer,
    String requiredAudience
) {}