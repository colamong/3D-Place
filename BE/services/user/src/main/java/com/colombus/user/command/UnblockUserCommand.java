package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record UnblockUserCommand (
    UUID userId,
    Instant updatedAtExpected
){}