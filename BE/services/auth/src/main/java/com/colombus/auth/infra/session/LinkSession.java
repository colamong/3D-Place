package com.colombus.auth.infra.session;

import java.time.Instant;
import java.util.UUID;

public record LinkSession(
    String  state,
    UUID    userId,
    String  provider,
    String  codeVerifier,
    String  codeChallenge,
    String  redirectUri,
    Instant expiresAt,
    String  ipHash,
    String  uaHash
) {
    public boolean expired() { return Instant.now().isAfter(expiresAt); }
}