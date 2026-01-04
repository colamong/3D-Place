package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;

public record ConfirmEmailChangeCommand(
    UUID userId,
    String verifiedEmail,
    Instant updatedAtExpected
) {}