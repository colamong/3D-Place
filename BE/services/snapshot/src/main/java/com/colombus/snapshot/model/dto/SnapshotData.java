package com.colombus.snapshot.model.dto;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.snapshot.glb.model.VoxelGeometry;

import java.util.List;

public record SnapshotData(
        SnapshotDataInterface finalSnapshot,
        String snapshotUrl,
        String glbUrl,
        int snapshotBytes,
        int glbBytes
) {}