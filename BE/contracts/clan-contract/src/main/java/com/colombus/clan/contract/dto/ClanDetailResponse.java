package com.colombus.clan.contract.dto;

import java.time.Instant;
import java.util.UUID;
import com.colombus.clan.contract.enums.ClanJoinPolicyCode;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

public record ClanDetailResponse(
    UUID id,
    String name,
    @Nullable String description,
    UUID ownerId,
    ClanJoinPolicyCode joinPolicy,
    boolean isPublic,
    int memberCount,
    long paintCountTotal,
    JsonNode metadata,
    Instant createdAt,
    Instant updatedAt
) {}