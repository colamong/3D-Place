package com.colombus.common.security.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.actuator")
public record ActuatorSecurityProperties(
    boolean enabled,
    String username,
    String password
) {}