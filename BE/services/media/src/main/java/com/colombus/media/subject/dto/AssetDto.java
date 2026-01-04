package com.colombus.media.subject.dto;

import java.util.UUID;

public record AssetDto(
    UUID assetId,
    String url,
    int width,
    int height,
    String contentType,
    String rawSha256,
    String contentSha256
) {}