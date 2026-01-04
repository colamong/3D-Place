package com.colombus.user.contract.dto;

import com.colombus.user.contract.enums.AccountRoleCode;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
    UUID userId,
    @Nullable String email,
    boolean emailVerified,
    @Nullable String nickname,
    @Nullable Integer nicknameSeq,
    @Nullable String nicknameHandle,
    boolean isActive,
    long loginCount,
    JsonNode metadata,
    int paintCountTotal,
    @Nullable Instant lastLoginAt,
    @Nullable Instant blockedAt,
    @Nullable String blockedReason,
    Instant createdAt,
    Instant updatedAt,
    List<AccountRoleCode> roles,
    List<UserIdentityDto> identities
) {}