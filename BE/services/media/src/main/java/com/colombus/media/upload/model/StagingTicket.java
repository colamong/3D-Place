package com.colombus.media.upload.model;

import java.time.Instant;
import java.util.UUID;

import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.kafka.subject.model.type.SubjectKind;

public record StagingTicket(
    UUID assetId,
    SubjectKind subjectKind,
    UUID subjectId,
    AssetPurpose purpose,
    String s3Key,
    Instant expiresAt,
    String status,
    Instant createdAt,
    Instant usedAt
) {}