package com.colombus.clan.model;

import java.time.Instant;
import java.util.UUID;

import com.colombus.clan.model.type.ClanJoinPolicy;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.Nullable;

public record ClanDetail(
    UUID id,
    String name,
    @Nullable String description,
    UUID ownerId,
    ClanJoinPolicy joinPolicy,
    boolean isPublic,
    int memberCount,
    long paintCountTotal,
    JsonNode metadata,
    Instant createdAt,
    Instant updatedAt
) {}