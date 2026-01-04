package com.colombus.common.domain.dto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ChunkInfo(String worldName, int lod, int tx, int ty, int x, int y, int z) {

    private static final Pattern CHUNK_PATTERN = Pattern.compile(
            ".*?\\{world:([^}]+)}(?::l(-?\\d+))?(?::tx(-?\\d+))?(?::ty(-?\\d+))?(?::x(-?\\d+))?(?::y(-?\\d+))?(?::z(-?\\d+))?"
    );
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile(
            "tx(-?\\d+):ty(-?\\d+):x(-?\\d+):y(-?\\d+):z(-?\\d+)"
    );


    public static ChunkInfo fromKey(String chunkKey) {
        if (chunkKey == null || chunkKey.isEmpty()) {
            throw new IllegalArgumentException("chunkKey가 비어 있습니다.");
        }

        Matcher matcher = CHUNK_PATTERN.matcher(chunkKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("유효하지 않은 chunkKey 형식: " + chunkKey);
        }

        String worldName = matcher.group(1);
        int lod = parseInt(matcher.group(2));
        int tx = parseInt(matcher.group(3));
        int ty = parseInt(matcher.group(4));
        int x = parseInt(matcher.group(5));
        int y = parseInt(matcher.group(6));
        int z = parseInt(matcher.group(7));

        return new ChunkInfo(worldName, lod, tx, ty, x, y, z);
    }

    public static ChunkInfo fromKey(String world, int lod, String chunkId) {
        if (chunkId == null || chunkId.isEmpty()) {
            throw new IllegalArgumentException("chunkId가 비어 있습니다.");
        }

        Matcher matcher = CHUNK_ID_PATTERN.matcher(chunkId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("유효하지 않은 chunkId 형식: " + chunkId);
        }

        int tx = Integer.parseInt(matcher.group(1));
        int ty = Integer.parseInt(matcher.group(2));
        int x = Integer.parseInt(matcher.group(3));
        int y = Integer.parseInt(matcher.group(4));
        int z = Integer.parseInt(matcher.group(5));

        return new ChunkInfo(world, lod, tx, ty, x, y, z);
    }

    public static ChunkInfo replaceLodInChunkKey(ChunkInfo chunkInfo, int lod) {
        return new ChunkInfo(chunkInfo.worldName(), lod, chunkInfo.tx(),  chunkInfo.ty(), chunkInfo.x(), chunkInfo.y(), chunkInfo.z());
    }

    public static String toChunkKey(ChunkInfo info) {
        return String.format("{world:%s}:l%d:tx%d:ty%d:x%d:y%d:z%d",
                info.worldName(), info.lod(), info.tx(), info.ty(),
                info.x(), info.y(), info.z());
    }

    private static int parseInt(String value) {
        return (value == null || value.isEmpty()) ? 0 : Integer.parseInt(value);
    }
}
