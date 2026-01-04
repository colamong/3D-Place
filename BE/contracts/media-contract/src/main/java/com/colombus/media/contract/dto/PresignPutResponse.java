package com.colombus.media.contract.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PresignPutResponse(
    UUID assetId,
    String method,
    String url,
    Map<String, List<String>> headers,
    String key,
    Instant expiresAt
) {}
