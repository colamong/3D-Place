package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record RestoreUserCommand(
    UUID userId,
    Boolean nullifyEmailIfConflict,
    Instant updatedAtExpected
) {}
