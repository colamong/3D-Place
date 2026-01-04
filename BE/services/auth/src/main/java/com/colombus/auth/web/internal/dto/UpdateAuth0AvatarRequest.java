package com.colombus.auth.web.internal.dto;

public record UpdateAuth0AvatarRequest(
    String auth0UserId,
    String avatarUrl
) {}