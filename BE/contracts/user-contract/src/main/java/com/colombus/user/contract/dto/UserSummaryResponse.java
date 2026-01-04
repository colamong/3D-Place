package com.colombus.user.contract.dto;

import java.util.UUID;

import jakarta.annotation.Nullable;

public record UserSummaryResponse(
    UUID id,
    String nickname,
    @Nullable String nicknameHandle
) {}