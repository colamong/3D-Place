package com.colombus.user.model;

import java.time.Instant;
import java.util.UUID;
import com.colombus.user.model.type.AuthProvider;
import jakarta.annotation.Nullable;

public record UserIdentity(
    UUID id,
    UUID userId,
    AuthProvider provider,
    String providerTenant,
    String providerSub,
    @Nullable String emailAtProvider,
    @Nullable String displayNameAtProvider,
    @Nullable Instant lastSyncAt,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant deletedAt
) {
    public boolean isDeleted() { return deletedAt != null; }
}