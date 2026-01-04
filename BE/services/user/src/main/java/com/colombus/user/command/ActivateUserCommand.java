package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record ActivateUserCommand (
    UUID userId,
    Boolean active,
    Instant updatedAtExpected
){}
