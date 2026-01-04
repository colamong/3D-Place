package com.colombus.auth.web.internal.dto;

public record UnlinkIdentityRequest(
    String primaryAuth0UserId,
    String provider,
    String providerUserId
) {}