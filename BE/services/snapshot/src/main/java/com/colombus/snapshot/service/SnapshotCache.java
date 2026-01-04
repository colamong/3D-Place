package com.colombus.snapshot.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.common.domain.dto.DeltaDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SnapshotCache {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCache.class);

    // Caffeine 캐시: key = "world:lLOD:txTY:tyTY:xX:yY:zZ", value = List<DeltaDTO>
    private final Cache<String, List<DeltaDTO>> cache = Caffeine.newBuilder()
            .maximumSize(100_000)                // 최대 항목 수 제한
            .expireAfterWrite(9, TimeUnit.MINUTES)
            .build();

    public List<DeltaDTO> get(ChunkInfo chunk) {
        String key = buildKey(chunk);
        return cache.getIfPresent(key);
    }

    public void put(ChunkInfo chunk, List<DeltaDTO> deltas) {
        String key = buildKey(chunk);
        cache.put(key, deltas);
        log.debug("캐시 저장: {}, Delta 수: {}", key, deltas.size());
    }

    public void clearLODCache(int lod) {
        cache.asMap().keySet().removeIf(key -> key.contains(":l" + lod + ":"));
        log.info("LOD {} 캐시 정리", lod);
    }

    public void clearAll() {
        cache.invalidateAll();
        log.info("전체 캐시 초기화");
    }

    private String buildKey(ChunkInfo chunk) {
        return String.format("%s:l%d:tx%d:ty%d:x%d:y%d:z%d",
                chunk.worldName(), 1,
                chunk.tx(), chunk.ty(),
                chunk.x(), chunk.y(), chunk.z());
    }
}