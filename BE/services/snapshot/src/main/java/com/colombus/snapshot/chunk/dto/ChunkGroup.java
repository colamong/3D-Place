package com.colombus.snapshot.chunk.dto;

import com.colombus.common.domain.dto.ChunkInfo;
import lombok.ToString;

import java.util.List;


public record ChunkGroup(
    String worldName,
    int lod,
    int baseTx,
    int baseTy,
    List<ChunkInfo> childChunk
) {

    public ChunkInfo getChunkInfoForMetadata() {
        if(lod == 3) {
            return new ChunkInfo(worldName, lod, baseTx,baseTy, -2, -2, 0);
        }
        int x = Math.floorDiv(childChunk.getFirst().x(), 2) * 2;
        int y = Math.floorDiv(childChunk.getFirst().y(), 2) * 2;
        int z = Math.floorDiv(childChunk.getFirst().z(), 2) * 2;
        return new ChunkInfo(worldName, lod, baseTx, baseTy, x, y, z);
    }
}
