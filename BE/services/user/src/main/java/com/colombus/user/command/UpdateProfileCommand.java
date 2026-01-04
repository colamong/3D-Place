package com.colombus.user.command;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

public record UpdateProfileCommand(
    UUID userId,
    @Nullable String nickname,
    @Nullable JsonNode metadataPatch,
    @Nullable Instant updatedAtExpected
) {}