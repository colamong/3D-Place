package com.colombus.clan.contract.dto;

import com.colombus.clan.contract.enums.ClanJoinPolicyCode;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

public record UpdateClanProfileRequest(
    @Nullable String name,
    @Nullable String description,
    @Nullable JsonNode metadataPatch,
    @Nullable ClanJoinPolicyCode joinPolicy
) {}