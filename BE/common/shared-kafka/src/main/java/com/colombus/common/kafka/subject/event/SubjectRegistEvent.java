package com.colombus.common.kafka.subject.event;

import java.time.Instant;
import java.util.UUID;
import com.colombus.common.kafka.subject.model.type.SubjectKind;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SubjectRegistEvent (
    SubjectKind kind,
    UUID subjectId,
    boolean alive,
    long version,
    @JsonProperty("ts") Instant occurredAt
) { }