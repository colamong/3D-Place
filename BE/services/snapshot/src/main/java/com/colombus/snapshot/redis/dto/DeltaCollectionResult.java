package com.colombus.snapshot.redis.dto;

import com.colombus.common.domain.dto.DeltaDTO;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record DeltaCollectionResult(
        Map<UUID, DeltaDTO> currentDeltas,
        Set<String> opIds,
        Set<String> tombstoneOpIds
) {}
