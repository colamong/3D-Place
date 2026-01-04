package com.colombus.clan.contract.dto;

import java.time.Instant;

public record ClanInviteTicketResponse(
    String code,
    String url,
    Instant expiresAt
) {}