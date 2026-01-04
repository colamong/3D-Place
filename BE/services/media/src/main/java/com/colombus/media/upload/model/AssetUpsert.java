package com.colombus.media.upload.model;

import com.colombus.common.kafka.subject.model.type.AssetPurpose;

public record AssetUpsert (
    AssetPurpose purpose,
    String rawSha256,
    String contentSha256,
    String contentType,
    long size,
    int width,
    int height,
    String storageBucket,
    String storageKey,
    String publicUrl
) {}
