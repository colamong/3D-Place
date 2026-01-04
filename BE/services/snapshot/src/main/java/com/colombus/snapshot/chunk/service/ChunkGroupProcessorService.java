package com.colombus.snapshot.chunk.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.snapshot.chunk.dto.ChunkGroup;
import com.colombus.snapshot.chunk.dto.ChunkGroupResult;
import com.colombus.snapshot.chunk.dto.ColorRGB;
import com.colombus.snapshot.chunk.dto.VoxelCoordinate;
import com.colombus.snapshot.exception.DataCorruptionException;
import com.colombus.snapshot.exception.StorageException;
import com.colombus.snapshot.glb.model.VoxelGeometry;
import com.colombus.snapshot.glb.service.GLBGeneratorService;
import com.colombus.snapshot.model.dto.SnapshotData;
import com.colombus.snapshot.model.dto.VoxelSnapshot;
import com.colombus.snapshot.model.type.SnapshotKind;
import com.colombus.snapshot.s3.service.S3StorageService;
import com.colombus.snapshot.service.SnapshotCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.colombus.snapshot.exception.SnapshotErrorCode.DATA_CORRUPTION_DETECTED;
import static com.colombus.snapshot.exception.SnapshotErrorCode.SNAPSHOT_PARSE_FAILED;

@Service
@RequiredArgsConstructor
public class ChunkGroupProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ChunkGroupProcessorService.class);

    private final SnapshotCache snapshotCache;
    private final ChunkMetadataService chunkMetadataService;
    private final S3StorageService s3Storage;
    private final GLBGeneratorService glbGenerator;
    private final ObjectMapper objectMapper;

    public ChunkGroupResult processChunkGroup(ChunkGroup group, int lod) {
        // groupKey : {world:world}:l2:tx0:ty1
        String groupKey = buildGroupKey(group, lod);

        try {
            log.info("청크 그룹 처리 시작: {}, LOD: {}", groupKey, lod);

            List<VoxelGeometry> mergedDeltas = loadAndMergeChildSnapshots(group, lod);
            log.info("현재 그룹의 병합된 데이터 셋 : " + mergedDeltas.toString());

            if (mergedDeltas.isEmpty()) {
                log.info("청크 그룹 데이터 없음: {}", groupKey);
                return ChunkGroupResult.success(groupKey, 0, null);
            }

            ChunkInfo chunkInfo = group.getChunkInfoForMetadata();
            UUID chunkUuid = chunkMetadataService.getOrCreateChunkIndex(chunkInfo);
            int curVersion = chunkMetadataService.getSnapshotVersion(chunkUuid);
            int newVersion = curVersion + 1;

            SnapshotData snapshotData = createSnapshotData(
                    chunkInfo, mergedDeltas, newVersion, lod
            );

            chunkMetadataService.saveSnapshotMetadata(
                    chunkUuid, snapshotData, newVersion,
                    SnapshotKind.MESH_SURFACE, true, group.childChunk()
            );

            return ChunkGroupResult.success(groupKey, mergedDeltas.size(), snapshotData.glbUrl());

        } catch (Exception e) {
            log.error("청크 그룹 처리 실패: {}", groupKey, e);
            return ChunkGroupResult.failure(groupKey, e.getMessage());
        }
    }

    private List<VoxelGeometry> loadAndMergeChildSnapshots(ChunkGroup group, int lod) {
        List<ChunkInfo> childChunks = group.childChunk();

        // 병렬로 자식 청크 스냅샷 로드
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<List<VoxelGeometry>>> futures = childChunks.stream()
                    .map(chunk -> executor.submit(() -> {
                        try {
                            // 캐시 확인
                            List<DeltaDTO> cached = snapshotCache.get(chunk);
                            List<DeltaDTO> deltas;
                            if (cached != null) {
                                log.debug("캐시 HIT: {}", chunk);
                                deltas = cached;
                            } else {
                                log.info("캐시 MISS: {}", chunk);
                                deltas = loadSnapshotFromS3(chunk);
                                if (!deltas.isEmpty()) snapshotCache.put(chunk, deltas);
                            }

                            // DeltaDTO → VoxelGeometry 변환 + LOD/Chunk 보정
                            return deltas.stream()
                                    .map(delta -> {
                                        VoxelCoordinate coord = VoxelCoordinate.fromDelta(delta);

                                        int x = adjustByLODChunk(coord.x(), chunk.x(), lod);
                                        int y = adjustByLODChunk(coord.y(), chunk.y(), lod);
                                        int z = coord.z() + (chunk.z() * 256);

                                        ColorRGB color = ColorRGB.fromBytes(delta.colorBytes());
                                        return new VoxelGeometry(x, y, z, color.r(), color.g(), color.b());
                                    })
                                    .collect(Collectors.toList());

                        } catch (Exception e) {
                            log.error("자식 청크 로드 실패: {}", chunk, e);
                            return List.<VoxelGeometry>of();
                        }
                    }))
                    .toList();


            // Future 결과 합치기
            return futures.stream()
                    .flatMap(f -> {
                        try {
                            return f.get().stream();
                        } catch (Exception e) {
                            log.error("Future get 실패", e);
                            return Stream.<VoxelGeometry>empty();
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    private List<DeltaDTO> loadSnapshotFromS3(ChunkInfo chunk) {
        try {
            // 최신 버전 조회
            UUID chunkUuid = chunkMetadataService.getOrCreateChunkIndex(chunk);
            int version = chunkMetadataService.getSnapshotVersion(chunkUuid);
            log.info("##### [loadSnapshotFromS3] ##### 현재 청크 정보 : {}, 현재 버전 : {}", chunk, version);
            if (version == 0) {
                log.debug("스냅샷 없음: {}", chunk);
                return List.of();
            }

            // S3에서 로드
            Optional<String> snapshotJson = s3Storage.getLatestSnapshot(chunk, version);

            if (snapshotJson.isEmpty()) {
                return List.of();
            }

            // JSON 파싱
            return objectMapper.readValue(
                    snapshotJson.get(),
                    new TypeReference<List<DeltaDTO>>() {}
            );

        } catch (JsonProcessingException e) {
            log.error("스냅샷 JSON 파싱 실패: {}", chunk, e);
            throw new DataCorruptionException(
                    SNAPSHOT_PARSE_FAILED,
                    "스냅샷 파싱 실패: " + chunk,
                    e
            );
        } catch (Exception e) {
            log.error("스냅샷 로드 실패: {}", chunk, e);
            return List.of();
        }
    }

    private SnapshotData createSnapshotData(
            ChunkInfo chunkInfo,
            List<VoxelGeometry> finalSnapshot,
            int newVersion,
            int lod) throws StorageException {

        String chunkKey = ChunkInfo.toChunkKey(chunkInfo);
        try {
            log.info("최종 스냅샷 Delta 수: {}", finalSnapshot.size());

            // Snapshot 생성, 업로드
            String snapshotJson = objectMapper.writeValueAsString(finalSnapshot);
            String snapshotUrl = s3Storage.uploadSnapshot(chunkInfo, newVersion, snapshotJson);
            log.info("스냅샷 업로드 완료: {}", snapshotUrl);

            byte[] glbData = glbGenerator.generateLODGLBWithGreedyMeshing(finalSnapshot, lod);
            String glbUrl = s3Storage.uploadGLB(chunkInfo, newVersion, glbData);
            log.info("GLB 업로드 완료: {}", glbUrl);

            return new SnapshotData(new VoxelSnapshot(finalSnapshot), snapshotUrl, glbUrl,
                    snapshotJson.length(), glbData.length);
        } catch (JsonProcessingException e) {
            // JSON 직렬화 실패
            log.error("[{}] 스냅샷 JSON 직렬화 실패", chunkKey, e);
            throw new DataCorruptionException(
                    DATA_CORRUPTION_DETECTED,
                    "스냅샷 직렬화 실패: " + chunkKey,
                    e
            );
        }
    }

    private String buildGroupKey(ChunkGroup group, int lod) {
        return String.format("{world:%s}:l%d:tx%d:ty%d",
                group.worldName(), lod, group.baseTx(), group.baseTy());
    }

    private int adjustByLODChunk(int voxelIdx, int chunkCoord, int lod) {
        if (Math.floorMod(chunkCoord, 2) != 0) {
            voxelIdx += 256;
        }
        if (lod == 3) {
            if (Math.floorDiv(chunkCoord, 2) >= 0) {
                voxelIdx += 512;
            }
        }
        return voxelIdx;
    }
}
