package com.colombus.user.contract.dto;

import java.util.List;
import java.util.UUID;

import com.colombus.user.contract.enums.AuthProviderCode;

import jakarta.annotation.Nullable;

public record UserIdentityBundle(
    UUID userId,
    List<ExternalIdentity> externalIdentities
) {
    public boolean hasAuth0() {
        return externalIdentities.stream()
            .anyMatch(id -> id.provider() == AuthProviderCode.AUTH0);
    }

    @Nullable
    public String firstAuth0SubOrNull() {
        return externalIdentities.stream()
            .filter(id -> id.provider() == AuthProviderCode.AUTH0)
            .map(ExternalIdentity::sub)
            .findFirst()
            .orElse(null);
    }
}