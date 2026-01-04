package com.colombus.media.contract.dto;

import java.util.UUID;

import com.colombus.media.contract.enums.AssetPurposeCode;

public record UploadCommitRequest(
    UUID assetId,
    String objectKey,
    AssetPurposeCode purpose,
    String originalFilename,
    String etag
) {}
