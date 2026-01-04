package com.colombus.user.contract.dto;

import com.colombus.user.contract.enums.AuthEventKindCode;
import com.colombus.user.contract.enums.AuthProviderCode;

import jakarta.annotation.Nullable;

public record AuthEventByIdentityRequest(
    AuthProviderCode provider,
    String providerTenant,
    String providerSub,
    @Nullable AuthEventKindCode kind,
    @Nullable String detail
) {}