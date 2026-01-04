package com.colombus.snapshot.chunk.dto;

public record ChunkGroupResult(
        String groupKey,
        boolean success,
        int deltaCount,
        String glbUrl,
        String errorMessage
) {
    public static ChunkGroupResult success(String groupKey, int deltaCount, String glbUrl) {
        return new ChunkGroupResult(groupKey, true, deltaCount, glbUrl, null);
    }

    public static ChunkGroupResult failure(String groupKey, String errorMessage) {
        return new ChunkGroupResult(groupKey, false, 0, null, errorMessage);
    }
}