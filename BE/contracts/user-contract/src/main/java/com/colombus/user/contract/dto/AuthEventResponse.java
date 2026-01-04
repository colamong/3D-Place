package com.colombus.user.contract.dto;

import java.time.Instant;
import java.util.UUID;

import com.colombus.user.contract.enums.AuthEventKindCode;
import com.colombus.user.contract.enums.AuthProviderCode;

import jakarta.annotation.Nullable;

public record AuthEventResponse(
    UUID id,
    @Nullable AuthProviderCode provider,
    @Nullable AuthEventKindCode kind,
    @Nullable String detail,
    @Nullable String ip,
    @Nullable String userAgent,
    Instant createdAt
) {}