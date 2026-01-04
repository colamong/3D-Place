package com.colombus.user.messaging.kafka.dto;

import java.time.Instant;
import java.util.UUID;

public record UserUnlinkFailed(
    UUID userId,
    String provider,
    String providerTenant,
    String providerSub,
    String reason,
    String message,
    int attempts,
    Instant at
) {}