package com.colombus.user.contract.dto;

import java.util.List;

public record DeleteIdentitiesRequest(
    List<ExternalIdentityPayload> identities,
    String reason
) {}