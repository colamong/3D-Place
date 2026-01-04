package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record StartEmailChangeCommand(
    UUID userId,
    String newEmail,
    Instant updatedAtExpected
) {}