package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record BlockUserCommand (
    UUID userId,
    String reason,
    Instant updatedAtExpected
){}
