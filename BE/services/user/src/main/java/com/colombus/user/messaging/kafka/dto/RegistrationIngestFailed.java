package com.colombus.user.messaging.kafka.dto;

import java.time.Instant;
import com.colombus.user.model.type.AuthProvider;

public record RegistrationIngestFailed(
    AuthProvider provider,
    String providerTenant,
    String providerSub,
    String email,
    boolean emailVerified,
    Instant createdAt,
    String ip,
    String userAgent,
    String failure,
    String message,
    int attempts,
    Instant occurredAt
) {}