package com.colombus.user.contract.dto;

import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

public record UpdateProfileRequest(
    @Nullable String nickname,
    @Nullable JsonNode metadata,
    @Nullable Instant updatedAtExpected
) {}