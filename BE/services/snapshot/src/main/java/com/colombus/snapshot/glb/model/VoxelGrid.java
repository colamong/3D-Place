package com.colombus.snapshot.glb.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class VoxelGrid {
    private final Map<Integer, VoxelGeometry> voxelMap = new HashMap<>();
    final int minX, minY, minZ;
    final int sizeX, sizeY, sizeZ;

    public VoxelGrid(List<VoxelGeometry> voxels) {
        if (voxels.isEmpty()) {
            this.minX = 0;
            this.minY = 0;
            this.minZ = 0;
            this.sizeX = 0;
            this.sizeY = 0;
            this.sizeZ = 0;
            return;
        }

        // 경계 계산
        int tempMinX = Integer.MAX_VALUE;
        int tempMinY = Integer.MAX_VALUE;
        int tempMinZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (VoxelGeometry v : voxels) {
            tempMinX = Math.min(tempMinX, v.x());
            tempMinY = Math.min(tempMinY, v.y());
            tempMinZ = Math.min(tempMinZ, v.z());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
            maxZ = Math.max(maxZ, v.z());
        }

        this.minX = tempMinX;
        this.minY = tempMinY;
        this.minZ = tempMinZ;
        this.sizeX = maxX - tempMinX + 1;
        this.sizeY = maxY - tempMinY + 1;
        this.sizeZ = maxZ - tempMinZ + 1;

        // 복셀 맵 구축
        for (VoxelGeometry v : voxels) {
            int localX = v.x() - this.minX;
            int localY = v.y() - this.minY;
            int localZ = v.z() - this.minZ;
            int key = localX + localY * this.sizeX + localZ * this.sizeX * this.sizeY;
            voxelMap.put(key, v);
        }
    }

    public boolean hasVoxel(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= sizeX ||
                localY < 0 || localY >= sizeY ||
                localZ < 0 || localZ >= sizeZ) {
            return false;
        }
        int key = localX + localY * sizeX + localZ * sizeX * sizeY;
        return voxelMap.containsKey(key);
    }

    public VoxelGeometry getVoxel(int localX, int localY, int localZ) {
        int key = localX + localY * sizeX + localZ * sizeX * sizeY;
        return voxelMap.get(key);
    }
}