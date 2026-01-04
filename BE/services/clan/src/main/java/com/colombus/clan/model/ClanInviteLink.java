package com.colombus.clan.model;

import java.time.Instant;

public record ClanInviteLink(
    String code,
    String url,
    Instant expiresAt
) {}