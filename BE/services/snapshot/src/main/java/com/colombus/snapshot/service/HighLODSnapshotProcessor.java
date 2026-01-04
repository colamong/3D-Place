package com.colombus.snapshot.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.chunk.dto.ChunkGroup;
import com.colombus.snapshot.chunk.dto.ChunkGroupResult;
import com.colombus.snapshot.chunk.service.ChunkAggregator;
import com.colombus.snapshot.chunk.service.ChunkGroupProcessorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class HighLODSnapshotProcessor {

    private static final Logger log = LoggerFactory.getLogger(HighLODSnapshotProcessor.class);

    private final ChunkAggregator chunkAggregator;
    private final ChunkGroupProcessorService chunkGroupProcessor;

    public void processAllLODLevels() {
        Instant batchStartTime = Instant.now();
        log.info("=== LOD 계층 배치 시작: {} ===", batchStartTime);

        try {
            List<ChunkInfo> processedLOD2Chunks = processLOD2();

            if (!processedLOD2Chunks.isEmpty()) {
                processLOD3(processedLOD2Chunks);
            } else {
                log.info("처리된 LOD 2 청크 없음. LOD 3 스킵");
            }
            log.info("=== LOD 계층 배치 완료 ===");

        } catch (Exception e) {
            log.error("=== LOD 계층 배치 실패 ===", e);
        }
    }

    private List<ChunkInfo> processLOD2() {
        log.info("=== LOD 2 처리 시작 ===");

        try {
            // LOD 0 업데이트 기준으로 LOD 2 그룹 계산
            List<ChunkGroup> lod2Groups = chunkAggregator.calculateLOD2Groups();

            if (lod2Groups.isEmpty()) {
                log.info("LOD 2 처리할 그룹 없음");
                return List.of();
            }

            log.info("LOD 2 처리 대상 그룹 수: {}", lod2Groups.size());

            // 병렬 처리
            List<ChunkInfo> processedChunks = processLODLevel(2, lod2Groups);

            log.info("=== LOD 2 처리 완료. 처리된 청크 수: {} ===", processedChunks.size());
            return processedChunks;

        } catch (Exception e) {
            log.error("=== LOD 2 처리 실패 ===", e);
            return List.of();
        }
    }

    private void processLOD3(List<ChunkInfo> processedLOD2Chunks) {
        log.info("=== LOD 3 처리 시작 ===");

        try {
            // 처리된 LOD 2 청크 기준으로 LOD 3 그룹 계산
            List<ChunkGroup> lod3Groups = chunkAggregator.calculateLOD3Groups(processedLOD2Chunks);

            if (lod3Groups.isEmpty()) {
                log.info("LOD 3 처리할 그룹 없음");
                return;
            }

            log.info("LOD 3 처리 대상 그룹 수: {}", lod3Groups.size());

            // 병렬 처리
            processLODLevel(3, lod3Groups);

            log.info("=== LOD 3 처리 완료 ===");

        } catch (Exception e) {
            log.error("=== LOD 3 처리 실패 ===", e);
        }
    }

    private List<ChunkInfo> processLODLevel(int lod, List<ChunkGroup> groups) {
        log.info("LOD {} 병렬 처리 시작. 그룹 수: {}", lod, groups.size());

        List<ChunkInfo> processedChunks = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ChunkGroupResult>> futures = groups.stream()
                    .map(group -> CompletableFuture.supplyAsync(
                            () -> chunkGroupProcessor.processChunkGroup(group, lod),
                            executor
                    ))
                    .toList();

            // 모든 그룹 처리 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 결과 통계 및 처리된 청크 수집
            List<ChunkGroupResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            long successCount = results.stream().filter(ChunkGroupResult::success).count();
            long failedCount = results.stream().filter(r -> !r.success()).count();

            if (lod == 2) {
                for (int i = 0; i < results.size(); i++) {
                    if (results.get(i).success()) {
                        // 해당 그룹의 모든 자식 청크 추가
                        processedChunks.addAll(groups.get(i).childChunk());
                    }
                }
            }

            log.info("LOD {} 처리 완료. 성공: {}, 실패: {}, 전체: {}",
                    lod, successCount, failedCount, groups.size());

        } catch (Exception e) {
            log.error("LOD {} 병렬 처리 중 오류", lod, e);
        }

        return processedChunks;
    }
}