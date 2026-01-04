package com.colombus.snapshot.glb.model;

// 복셀의 위치, 색상 정보
public record VoxelGeometry(
    int x,
    int y,
    int z,
    float r,
    float g,
    float b
) { }
