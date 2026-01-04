package com.colombus.clan.contract.dto;

import java.util.UUID;

import com.colombus.clan.contract.enums.ClanJoinRequestStatusCode;

public record MyClanJoinRequestStatusResponse(
    UUID clanId,
    ClanJoinRequestStatusCode status
) {}