package com.colombus.user.model;

import java.time.Instant;
import java.util.UUID;
import com.colombus.user.model.type.AuthEventKind;
import com.colombus.user.model.type.AuthProvider;

public record AuthEvent(
    UUID id,
    UUID userId,
    AuthProvider provider,
    AuthEventKind kind,
    String detail,
    String ipAddr,
    String userAgent,
    Instant createdAt
) {}