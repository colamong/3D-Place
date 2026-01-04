package com.colombus.user.contract.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

public record PublicProfileResponse(
    UUID userId,
    @Nullable String nickname,
    @Nullable Integer nicknameSeq,
    @Nullable String nicknameHandle,
    JsonNode metadata,
    int paintCountTotal,
    Instant createdAt
) {}