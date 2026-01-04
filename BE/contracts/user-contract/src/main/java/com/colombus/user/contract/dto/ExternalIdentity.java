package com.colombus.user.contract.dto;

import com.colombus.user.contract.enums.AuthProviderCode;

import jakarta.annotation.Nullable;

public record ExternalIdentity(
    AuthProviderCode provider,
    @Nullable String tenant,
    String sub
) {}