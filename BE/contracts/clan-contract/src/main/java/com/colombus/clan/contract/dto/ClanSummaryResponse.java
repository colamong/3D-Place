package com.colombus.clan.contract.dto;

import java.util.UUID;
import com.colombus.clan.contract.enums.ClanJoinPolicyCode;
import jakarta.annotation.Nullable;

public record ClanSummaryResponse(
    UUID id,
    String name,
    @Nullable String description,
    boolean isPublic,
    ClanJoinPolicyCode joinPolicy,
    int memberCount,
    long paintCountTotal
) {}