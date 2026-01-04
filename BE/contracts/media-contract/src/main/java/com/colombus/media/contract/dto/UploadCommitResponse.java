package com.colombus.media.contract.dto;

import java.util.UUID;

public record UploadCommitResponse(
    UUID assetId,
    String assetKey,
    long size,
    double width,
    double height,
    String mime,
    String sha256,
    String pixelSha256,
    String cdnUrl
) {}