package com.colombus.snapshot.glb.model;

// glTF 구조의 인덱스 추적
public class VoxelIndexTracker {
    public static final int BUFFER_VIEWS_PER_VOXEL = 3;  // positions, colors, indices
    public static final int ACCESSORS_PER_VOXEL = 3;

    public int nodeIndex = 0;
    public int meshIndex = 0;
    public int accessorIndex = 0;
    public int bufferViewIndex = 0;

    public void increment() {
        nodeIndex++;
        meshIndex++;
        accessorIndex += ACCESSORS_PER_VOXEL;
        bufferViewIndex += BUFFER_VIEWS_PER_VOXEL;
    }
}
