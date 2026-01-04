package com.colombus.user.contract.dto;

import java.time.Instant;
import java.util.UUID;

import com.colombus.user.contract.enums.AuthProviderCode;

import jakarta.annotation.Nullable;

public record UserIdentityDto(
    UUID id,
    UUID userId,
    AuthProviderCode provider,
    String providerTenant,
    String providerSub,
    @Nullable String emailAtProvider,
    @Nullable String displayNameAtProvider,
    @Nullable Instant lastSyncAt,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant deletedAt
) {}
