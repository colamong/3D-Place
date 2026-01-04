package com.colombus.auth.infra.usersvc.dto;

import java.util.UUID;

public record IdentityDto(
    UUID id,
    String provider,
    String tenant,
    String sub,
    boolean primary
) {}