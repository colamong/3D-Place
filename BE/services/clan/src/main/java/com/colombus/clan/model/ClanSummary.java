package com.colombus.clan.model;

import java.util.UUID;
import com.colombus.clan.model.type.ClanJoinPolicy;
import jakarta.annotation.Nullable;

public record ClanSummary (
    UUID id,
    String name,
    @Nullable String description,
    boolean isPublic,
    ClanJoinPolicy joinPolicy,
    int memberCount,
    long paintCountTotal
) {}