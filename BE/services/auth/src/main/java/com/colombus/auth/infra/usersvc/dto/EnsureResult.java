package com.colombus.auth.infra.usersvc.dto;

import java.util.UUID;

public record EnsureResult(
    UUID userId,
    boolean signup,
    boolean link,
    boolean sync,
    boolean login
) {}