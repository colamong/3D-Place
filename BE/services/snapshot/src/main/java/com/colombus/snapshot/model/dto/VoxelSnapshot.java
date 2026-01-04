package com.colombus.snapshot.model.dto;

import com.colombus.snapshot.glb.model.VoxelGeometry;

import java.util.List;

public record VoxelSnapshot(List<VoxelGeometry> voxels) implements SnapshotDataInterface
{
    @Override
    public int size() {
        return voxels.size();
    }
}
