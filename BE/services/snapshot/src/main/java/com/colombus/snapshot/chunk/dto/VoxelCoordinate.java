package com.colombus.snapshot.chunk.dto;

import com.colombus.common.domain.dto.DeltaDTO;

import static com.colombus.snapshot.glb.service.GLBGeneratorService.*;

public record VoxelCoordinate(int x, int y, int z) {
        public static VoxelCoordinate fromDelta(DeltaDTO delta) {
            int x = (delta.voxelId() >> VOXEL_X_SHIFT) & VOXEL_ID_MASK;
            int y = (delta.voxelId() >> VOXEL_Y_SHIFT) & VOXEL_ID_MASK;
            int z = delta.voxelId() & VOXEL_ID_MASK;
            return new VoxelCoordinate(x, y, z);
        }

       public VoxelCoordinate applyLOD(int lod) {
            if (lod <= 1) return this;
            int divisor = (int) Math.pow(2.0, lod - 1);
            return new VoxelCoordinate(x / divisor, y / divisor, z / divisor);
        }

        public int toKey() {
            return (x << VOXEL_X_SHIFT) | (y << VOXEL_Y_SHIFT) | z;
        }
    }