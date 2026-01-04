package com.colombus.user.messaging.kafka.dto;

import java.util.UUID;

public record UserUnlinkPayload (
    UUID userId,
    String provider,
    String providerTenant,
    String providerSub
) {}
