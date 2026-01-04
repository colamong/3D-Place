package com.colombus.user.model;

import com.colombus.user.model.type.AccountRole;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 1:1 mapping to account.user_account
 * - NOT NULL 컬럼은 primitive/nonnull 타입으로 반영
 * - JSONB(metadata)는 JsonNode로 보관
 */
public record UserAccount(
    UUID id,                 // PK
    @Nullable String email,
    @Nullable String nickname,
    @Nullable Integer nicknameSeq,
    @Nullable String nicknameHandle,
    List<AccountRole> roles,
    boolean isActive,          // NOT NULL DEFAULT true
    boolean emailVerified,     // NOT NULL DEFAULT false
    JsonNode metadata,         // NOT NULL DEFAULT '{}'::jsonb
    int paintCountTotal,

    @Nullable Instant lastLoginAt,
    long loginCount,           // NOT NULL DEFAULT 0

    @Nullable Instant blockedAt,
    @Nullable String blockedReason,

    Instant createdAt,         // NOT NULL DEFAULT now()
    Instant updatedAt,         // NOT NULL DEFAULT now()
    @Nullable Instant deletedAt
) {}