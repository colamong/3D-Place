package com.colombus.snapshot.chunk.dto;

public record ChunkProcessResult(
        String chunkKey,
        boolean success,
        int deltaCount,
        String snapshotUrl,
        String glbUrl,
        String errorMessage
) {
    public static ChunkProcessResult success(String chunkKey, int deltaCount,
                                             String snapshotUrl, String glbUrl) {
        return new ChunkProcessResult(chunkKey, true, deltaCount, snapshotUrl, glbUrl, null);
    }

    public static ChunkProcessResult failure(String chunkKey, String errorMessage) {
        return new ChunkProcessResult(chunkKey, false, 0, null, null, errorMessage);
    }
}
