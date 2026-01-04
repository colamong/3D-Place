package com.colombus.world.dto.response;

import com.colombus.common.domain.dto.EraseResponse;
import com.colombus.common.domain.dto.PaintResponse;

import java.util.List;

public record UrlResponse(
        String snapshotUrl,
        String glbUrl,
        List<PaintResponse> paintResponses,
        List<EraseResponse> eraseResponses
) {}
