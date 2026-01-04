package com.colombus.common.kafka.subject.event;

import java.time.Instant;
import java.util.UUID;

import com.colombus.common.kafka.subject.model.type.AssetPurpose;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SubjectAssetUpsertEvent(
    SubjectKind subjectKind,
    UUID subjectId,
    AssetPurpose purpose,
    UUID assetId,
    String publicUrl,
    long version,
    @JsonProperty("ts") Instant occurredAt
) {}