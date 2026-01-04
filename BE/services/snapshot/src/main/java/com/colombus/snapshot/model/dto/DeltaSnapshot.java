package com.colombus.snapshot.model.dto;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.snapshot.glb.model.VoxelGeometry;

import java.util.List;

public record DeltaSnapshot(List<DeltaDTO> deltas) implements SnapshotDataInterface
{

    @Override
    public int size() {
        return deltas.size();
    }
}

