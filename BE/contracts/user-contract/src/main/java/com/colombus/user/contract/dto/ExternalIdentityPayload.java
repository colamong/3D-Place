package com.colombus.user.contract.dto;

import com.colombus.user.contract.enums.AuthProviderCode;

import jakarta.annotation.Nullable;

public record ExternalIdentityPayload(
    AuthProviderCode provider,
    @Nullable String tenant,
    String sub
) {}