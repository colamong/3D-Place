package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record SoftDeleteUserCommand(
    UUID userId,
    Instant updatedAtExpected
) {}
