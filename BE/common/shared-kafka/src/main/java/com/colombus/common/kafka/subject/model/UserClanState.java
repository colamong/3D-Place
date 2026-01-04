package com.colombus.common.kafka.subject.model;

import java.time.Instant;
import java.util.UUID;

public record UserClanState(
    UUID userId,
    UUID clanId,
    boolean active,
    Instant updatedAt
) {}