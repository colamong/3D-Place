package com.colombus.snapshot.chunk.service;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.chunk.dto.ChunkProcessResult;
import com.colombus.snapshot.chunk.dto.ColorRGB;
import com.colombus.snapshot.chunk.dto.VoxelCoordinate;
import com.colombus.snapshot.exception.*;
import com.colombus.snapshot.glb.model.VoxelGeometry;
import com.colombus.snapshot.glb.service.GLBGeneratorService;
import com.colombus.snapshot.model.dto.DeltaSnapshot;
import com.colombus.snapshot.model.dto.SnapshotData;
import com.colombus.snapshot.model.dto.SnapshotDataInterface;
import com.colombus.snapshot.model.dto.VoxelSnapshot;
import com.colombus.snapshot.model.type.SnapshotKind;
import com.colombus.snapshot.redis.dto.DeltaCollectionResult;
import com.colombus.snapshot.redis.service.RedisOperationService;
import com.colombus.snapshot.s3.service.S3StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.colombus.snapshot.exception.SnapshotErrorCode.*;

@Service
@RequiredArgsConstructor
public class ChunkProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ChunkProcessorService.class);


    private final RedisOperationService  redisOperationService;
    private final ChunkMergeService chunkMergeService;
    private final ChunkMetadataService chunkMetadataService;
    private final S3StorageService s3Storage;
    private final ObjectMapper objectMapper;
    private final GLBGeneratorService glbService;

    public ChunkProcessResult processChunk(String chunkKey, Instant batchStartTime) {

        log.info("청크 처리 시작: {}", chunkKey);

        try {
            ChunkInfo lod0ChunkInfo = ChunkInfo.fromKey(chunkKey);

            // redis에서 delta 정보 읽어옴
            DeltaCollectionResult deltaResult = redisOperationService.collectDeltasWithLock(chunkKey, batchStartTime);

            // 데이터 없으면 종료
            if (deltaResult.currentDeltas().isEmpty()) {
                log.info("적용할 Delta 없음. 청크: {}", chunkKey);
                return ChunkProcessResult.success(chunkKey, 0, null, null);
            }

            // 특정 chunkId에 대해 배치처리 할 op_id가 있음
            //////////////////////////////
            ///  LOD 0
            //////////////////////////////
//            processLOD(0, lod0ChunkInfo, deltaResult, SnapshotKind.SPARSE_VOXEL);
            UUID lod0ChunkUuid = chunkMetadataService.getOrCreateChunkIndex(lod0ChunkInfo);
            int lod0CurVersion = chunkMetadataService.getSnapshotVersion(lod0ChunkUuid);
            int lod0NewVersion = lod0CurVersion + 1;

            SnapshotData lod0SnapshotData = createSnapshotData(
                    lod0ChunkInfo, deltaResult, lod0CurVersion, lod0NewVersion, 0
            );
            chunkMetadataService.saveSnapshotMetadata(
                    lod0ChunkUuid, lod0SnapshotData, lod0NewVersion,
                    SnapshotKind.SPARSE_VOXEL, false
            );

            //////////////////////////////
            ///  LOD 1
            //////////////////////////////
//            processLOD(1, lod0ChunkInfo, deltaResult, SnapshotKind.MESH_SURFACE);
            ChunkInfo lod1ChunkInfo = ChunkInfo.replaceLodInChunkKey(lod0ChunkInfo, 1);
            UUID lod1ChunkUuid = chunkMetadataService.getOrCreateChunkIndex(lod1ChunkInfo);
            int lod1CurVersion = chunkMetadataService.getSnapshotVersion(lod1ChunkUuid);
            int lod1NewVersion = lod1CurVersion + 1;

            SnapshotData lod1SnapshotData = createSnapshotData(
                    lod1ChunkInfo, deltaResult, lod1CurVersion, lod1NewVersion, 1
            );
            chunkMetadataService.saveSnapshotMetadata(
                    lod1ChunkUuid, lod1SnapshotData, lod1NewVersion,
                    SnapshotKind.MESH_SURFACE, false
            );

            try {
                redisOperationService.cleanupProcessedDataWithLock(
                        chunkKey,
                        deltaResult.opIds(),
                        deltaResult.tombstoneOpIds(),
                        batchStartTime
                );
            } catch (Exception e) {
                log.warn("[{}] Redis 정리 실패. 다음 배치에 재처리", chunkKey, e);
            }

            log.info("청크 처리 완료: {}", chunkKey);
            return ChunkProcessResult.success(
                    chunkKey, lod0SnapshotData.finalSnapshot().size(),
                    lod0SnapshotData.snapshotUrl(), lod0SnapshotData.glbUrl()
            );

        } catch (DataCorruptionException e) {
            // 데이터 손상
            log.error("[{}] 데이터 손상 감지 (Fast Fail). 에러코드: {}", chunkKey, e.getCode(), e);
            throw e;
        } catch (DatabaseOperationException e) {
            // DB 관련 예외
            log.error("[{}] DB 작업 실패. 에러코드: {}", chunkKey, e.getCode(), e);
            throw e;
        } catch (LockAcquisitionException e) {
            // 락 획득 실패
            log.error("[{}] 락 획득 재시도 실패. 에러코드: {}", chunkKey, e.getCode(), e);
            throw e;
        } catch (RedisOperationException e) {
            // Redis 작업 실패
            log.error("[{}] Redis 작업 실패. 에러코드: {}", chunkKey, e.getCode(), e);
            throw e;
        } catch (StorageException e) {
            // S3 storage 작업 실패
            log.error("[{}] S3 storage 작업 실패. 에러코드: {}", chunkKey, e.getCode(), e);
            throw e;
        } catch (IllegalArgumentException e) {
            // ChunkInfo.fromKey() 실패 - 잘못된 청크 키 형식
            log.error("[{}] 유효하지 않은 청크 키", chunkKey, e);
            throw new DataCorruptionException(
                    CHUNK_KEY_INVALID,
                    "유효하지 않은 청크 키: " + chunkKey,
                    e
            );
        } catch (Exception e) {
            // 예상치 못한 예외
            log.error("[{}] 알 수 없는 오류로 청크 처리 실패", chunkKey, e);
            throw new RuntimeException("청크 처리 중 예상치 못한 실패: " + chunkKey, e);
        }
    }

    private SnapshotData createSnapshotData(
            ChunkInfo chunkInfo,
            DeltaCollectionResult deltaResult,
            int curVersion,
            int newVersion,
            int lod) throws StorageException {

        String chunkKey = ChunkInfo.toChunkKey(chunkInfo);
        try {
            // 최신스냅샷 + deltas - tombstone
            List<DeltaDTO> finalSnapshot = chunkMergeService.mergeChunk(
                    chunkInfo,
                    deltaResult.currentDeltas(),
                    deltaResult.tombstoneOpIds(),
                    curVersion
            );

            log.info("최종 스냅샷 Delta 수: {}", finalSnapshot.size());

            // Snapshot 생성, 업로드
            String snapshotJson = objectMapper.writeValueAsString(finalSnapshot);
            String snapshotUrl = s3Storage.uploadSnapshot(chunkInfo, newVersion, snapshotJson);
            log.info("스냅샷 업로드 완료: {}", snapshotUrl);


            SnapshotDataInterface data;
            if(lod == 0) {
                data =  new DeltaSnapshot(finalSnapshot);
            } else {
                // DeltaDTO → VoxelGeometry 변환 (좌표 그대로 사용)
                List<VoxelGeometry> voxels = finalSnapshot.stream()
                        .map(delta -> {
                            VoxelCoordinate coord = VoxelCoordinate.fromDelta(delta);
                            ColorRGB color = ColorRGB.fromBytes(delta.colorBytes());
                            return new VoxelGeometry(coord.x(), coord.y(), coord.z(), color.r(), color.g(), color.b());
                        })
                        .toList();
                data = new VoxelSnapshot(voxels);
            }

            // glb 생성, 업로드
            byte[] glbData = generateGLB(data, lod);
            String glbUrl = s3Storage.uploadGLB(chunkInfo, newVersion, glbData);
            log.info("GLB 업로드 완료: {}", glbUrl);

            return new SnapshotData(data, snapshotUrl, glbUrl,
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

    private byte[] generateGLB(SnapshotDataInterface snapshot, int lod) {
        log.info("snapshot의 타입 : {}, {}", snapshot.getClass().getSimpleName(), lod);
        if (lod == 0 && snapshot instanceof DeltaSnapshot ds) {
            return glbService.generateGLBWithSeparateMeshes(ds.deltas());
        } else if (lod == 1 && snapshot instanceof VoxelSnapshot vs) {
            return glbService.generateLODGLBWithGreedyMeshing(vs.voxels(), lod);
        } else {
            throw new IllegalArgumentException("Unsupported LOD or snapshot type");
        }
    }

    private void processLOD(
            int lod,
            ChunkInfo baseChunkInfo,
            DeltaCollectionResult deltaResult,
            SnapshotKind kind
    ) {
        ChunkInfo targetChunk = (lod == 0)
                ? baseChunkInfo
                : ChunkInfo.replaceLodInChunkKey(baseChunkInfo, lod);

        UUID uuid = chunkMetadataService.getOrCreateChunkIndex(targetChunk);

        int curVersion = chunkMetadataService.getSnapshotVersion(uuid);
        int newVersion = curVersion + 1;

        SnapshotData snapshotData = createSnapshotData(
                targetChunk, deltaResult, curVersion, newVersion, lod
        );

        chunkMetadataService.saveSnapshotMetadata(uuid, snapshotData, newVersion, kind, false);
    }

}
