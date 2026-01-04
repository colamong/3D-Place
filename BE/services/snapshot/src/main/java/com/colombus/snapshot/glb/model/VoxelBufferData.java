package com.colombus.snapshot.glb.model;

import java.util.List;

// 복셀의 버퍼 데이터 (정점, 색상, 인덱스)
public record VoxelBufferData(
    List<Float> positions,
    List<Float> colors,
    List<Integer> indices
) { }
