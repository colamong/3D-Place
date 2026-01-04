package com.colombus.snapshot.chunk.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.chunk.dto.ChunkGroup;
import com.colombus.snapshot.chunk.repo.ChunkRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChunkAggregator {

    private static final Logger log = LoggerFactory.getLogger(ChunkAggregator.class);

    private final ChunkRepository repository;

    public List<ChunkGroup> calculateLOD2Groups() {
        log.info("LOD 2 그룹 계산 시작");

        List<ChunkInfo> updatedLOD0Chunks = repository.findRecentlyUpdatedChunks(1);

        if (updatedLOD0Chunks.isEmpty()) {
            log.info("최근 업데이트된 LOD 0 청크 없음");
            return List.of();
        }

        log.info("최근 업데이트된 LOD 0 청크 수: {}", updatedLOD0Chunks.size());

        List<ChunkGroup> groups = buildLOD2Groups(updatedLOD0Chunks);
        log.info("LOD 2 그룹 계산 완료. 그룹 수: {}", groups.size());

        return groups;
    }

    public List<ChunkGroup> calculateLOD3Groups(List<ChunkInfo> processedLOD2Chunks) {
        log.info("LOD 3 그룹 계산 시작. 입력 LOD 2 청크 수: {}", processedLOD2Chunks.size());

        if (processedLOD2Chunks.isEmpty()) {
            return List.of();
        }

        List<ChunkGroup> groups = buildLOD3Groups(processedLOD2Chunks);
        log.info("LOD 3 그룹 계산 완료. 그룹 수: {}", groups.size());

        return groups;
    }

    private List<ChunkGroup> buildLOD2Groups(List<ChunkInfo> updatedLOD0Chunks) {
        // 월드별로 영향받는 LOD 1 청크 좌표 추출
        Map<String, Set<String>> worldAffectedChunks = new HashMap<>();

        for (ChunkInfo lod0Chunk : updatedLOD0Chunks) {
            String chunkKey = String.format("%d:%d:%d:%d:%d",
                    lod0Chunk.tx(), lod0Chunk.ty(),
                    lod0Chunk.x(), lod0Chunk.y(), lod0Chunk.z());

            worldAffectedChunks.computeIfAbsent(lod0Chunk.worldName(), k -> new HashSet<>())
                    .add(chunkKey);
        }

        // LOD 2 그룹으로 묶기
        Map<String, Set<String>> worldGroups = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : worldAffectedChunks.entrySet()) {
            String worldName = entry.getKey();

            for (String chunkKey : entry.getValue()) {
                String[] parts = chunkKey.split(":");
                int tx = Integer.parseInt(parts[0]);
                int ty = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);

                String groupKey = calculateLOD2GroupKey(tx, ty, x, y, z);
                worldGroups.computeIfAbsent(worldName, k -> new HashSet<>())
                        .add(groupKey);
            }
        }

        // ChunkGroup 생성
        List<ChunkGroup> result = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : worldGroups.entrySet()) {
            String worldName = entry.getKey();

            for (String groupKey : entry.getValue()) {
                String[] parts = groupKey.split(":");
                int baseTx = Integer.parseInt(parts[0]);
                int baseTy = Integer.parseInt(parts[1]);
                int baseX = Integer.parseInt(parts[2]);
                int baseY = Integer.parseInt(parts[3]);
                int baseZ = Integer.parseInt(parts[4]);

                // LOD 1 청크 중 실제 존재하는 것만 필터링
                List<ChunkInfo> childChunks = calculateLOD2ChildChunks(
                        worldName, baseTx, baseTy, baseX, baseY, baseZ
                );

                if (!childChunks.isEmpty()) {
                    result.add(new ChunkGroup(worldName, 2, baseTx, baseTy, childChunks));
                }
            }
        }

        return result;
    }

    private List<ChunkGroup> buildLOD3Groups(List<ChunkInfo> processedLOD2Chunks) {
        log.debug("[buildLOD3Groups] processedLOD2Chunks Size: " + processedLOD2Chunks.size());

        // 월드별로 영향받는 타일 추출
        Map<String, Set<String>> worldTiles = new HashMap<>();

        for (ChunkInfo lod2Chunk : processedLOD2Chunks) {
            String tileKey = String.format("%d:%d", lod2Chunk.tx(), lod2Chunk.ty());
            worldTiles.computeIfAbsent(lod2Chunk.worldName(), k -> new HashSet<>())
                    .add(tileKey);
        }
        log.debug("[buildLOD3Groups] worldTiles size: {}", worldTiles.size());
        // ChunkGroup 생성
        List<ChunkGroup> result = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : worldTiles.entrySet()) {
            String worldName = entry.getKey();

            for (String tileKey : entry.getValue()) {
                String[] parts = tileKey.split(":");
                int tx = Integer.parseInt(parts[0]);
                int ty = Integer.parseInt(parts[1]);

                // LOD 2 청크 8개 (2×2×2) 모두 포함
                // processedLOD2Chunks에 있으면 생성된 것 → 필터링 불필요
                List<ChunkInfo> childChunks = calculateLOD3ChildChunks(
                        worldName, tx, ty, processedLOD2Chunks
                );
                if (!childChunks.isEmpty()) {
                    result.add(new ChunkGroup(worldName, 3, tx, ty, childChunks));
                }
            }
        }

        return result;
    }

    private List<ChunkInfo> calculateLOD2ChildChunks(String worldName, int tx, int ty,
                                                     int baseX, int baseY, int baseZ) {
        // 가능한 8개 청크
        List<ChunkInfo> candidates = new ArrayList<>();

        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    candidates.add(new ChunkInfo(
                            worldName, 1,
                            tx, ty,
                            baseX + dx, baseY + dy, baseZ + dz
                    ));
                }
            }
        }

        // DB 필터링
        return filterExistingChunks(candidates, 1);
    }

    private List<ChunkInfo> calculateLOD3ChildChunks(String worldName, int tx, int ty,
                                                     List<ChunkInfo> processedLOD2Chunks) {
        // 해당 타일의 LOD 2 청크 8개
        List<ChunkInfo> childChunks = new ArrayList<>();

        for(ChunkInfo chunk : processedLOD2Chunks) {
            if(checkWorldAndTileCoord(chunk, worldName, tx, ty)) {
                childChunks.add(chunk);
            }
        }
        return childChunks;
    }

    private boolean checkWorldAndTileCoord(ChunkInfo  chunk, String worldName, int tx, int ty) {
        return chunk.worldName().equals(worldName)
                && chunk.tx() == tx
                && chunk.ty() == ty;
    }

    private String calculateLOD2GroupKey(int tx, int ty, int x, int y, int z) {
        int groupX = Math.floorDiv(x, 2) * 2;
        int groupY = Math.floorDiv(y, 2) * 2;
        int groupZ = Math.floorDiv(z, 2) * 2;
        return String.format("%d:%d:%d:%d:%d", tx, ty, groupX, groupY, groupZ);
    }

    private List<ChunkInfo> filterExistingChunks(List<ChunkInfo> candidates, int lod) {
        if (candidates.isEmpty()) return List.of();
        List<ChunkInfo> existing = repository.findExistingChunks(candidates, lod);
        return existing;
    }
}