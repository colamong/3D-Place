package com.colombus.clan.contract.dto;

import java.time.Instant;
import java.util.UUID;
import com.colombus.clan.contract.enums.ClanMemberRoleCode;
import com.colombus.clan.contract.enums.ClanMemberStatusCode;
import jakarta.annotation.Nullable;

public record ClanMemberDetailResponse(
    UUID id,
    UUID clanId,
    UUID userId,
    ClanMemberRoleCode role,
    ClanMemberStatusCode status,
    long paintCountTotal,
    Instant joinedAt,
    @Nullable Instant leftAt
) {}