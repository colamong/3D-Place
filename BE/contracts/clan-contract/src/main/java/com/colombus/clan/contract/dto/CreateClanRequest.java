package com.colombus.clan.contract.dto;

import com.colombus.clan.contract.enums.ClanJoinPolicyCode;
import jakarta.annotation.Nullable;

public record CreateClanRequest(
    String name,
    @Nullable String description,
    @Nullable ClanJoinPolicyCode policy
) {}