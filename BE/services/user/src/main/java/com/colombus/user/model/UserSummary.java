package com.colombus.user.model;

import java.util.UUID;

public record UserSummary(
    UUID id,
    String nickname,
    String nicknameHandle
) {}
