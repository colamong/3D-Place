package com.colombus.user.contract.dto;

import com.colombus.user.contract.enums.AuthProviderCode;

public record EnsureUserRequest(
    AuthProviderCode provider,
    String providerTenant,
    String providerSub,
    String email,
    boolean emailVerified,
    String locale,
    String avatarUrl
) {}