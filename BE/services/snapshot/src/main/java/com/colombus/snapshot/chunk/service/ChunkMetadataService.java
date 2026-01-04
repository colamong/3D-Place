package com.colombus.snapshot.chunk.service;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.chunk.repo.ChunkRepository;
import com.colombus.snapshot.exception.DatabaseOperationException;
import com.colombus.snapshot.model.dto.SnapshotData;
import com.colombus.snapshot.model.type.SnapshotKind;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static com.colombus.snapshot.exception.SnapshotErrorCode.*;

@Service
@RequiredArgsConstructor
public class ChunkMetadataService {

    private static final Logger log = LoggerFactory.getLogger(ChunkMetadataService.class);
    private final ChunkRepository repository;

    public UUID getOrCreateChunkIndex(ChunkInfo chunkInfo) {
        String chunkKey = ChunkInfo.toChunkKey(chunkInfo);
        try {
            UUID worldUuid = repository.findWorldUuidByName(chunkInfo.worldName())
                    .orElseThrow(() -> new DatabaseOperationException(
                            DB_CONSTRAINT_VIOLATION,
                            "존재하지 않는 WorldName: " + chunkInfo.worldName() + ", chunkKey: " + chunkKey
                    ));
            return repository.findChunkIndexUuid(
                    worldUuid,
                    (short) chunkInfo.lod(),
                    chunkInfo.tx(),
                    chunkInfo.ty(),
                    chunkInfo.x(),
                    chunkInfo.y(),
                    chunkInfo.z()
            ).orElseGet(() -> createChunkIndex(worldUuid, chunkInfo, chunkKey));

        } catch (DatabaseOperationException e) {
            throw e;

        } catch (DataAccessException e) {
            log.error("DB 연결 실패. chunkKey: {}", chunkKey, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "DB 연결 실패: " + chunkKey,
                    e
            );
        } catch (Exception e) {
            log.error("청크 인덱스 처리 실패. chunkKey: {}", chunkKey, e);
            throw new DatabaseOperationException(
                    CHUNK_PROCESSING_FAILED,
                    "청크 인덱스 처리 실패: " + chunkKey,
                    e
            );
        }
    }

    @Transactional
    protected UUID createChunkIndex(UUID worldUuid, ChunkInfo chunkInfo, String chunkKey) {
        try {
            log.info("새로운 청크 인덱스 생성: {}", chunkInfo);

            ChunkRepository.WorldLodInfo lodInfo = repository.findWorldLodInfo(worldUuid, (short) chunkInfo.lod())
                    .orElseThrow(() -> new DatabaseOperationException(
                            DB_CONSTRAINT_VIOLATION,
                            "World_lod Metadata가 존재하지 않음. chunkKey: " + chunkKey
                    ));

            double voxelSize = lodInfo.voxelSizeM();
            int edgeCells = lodInfo.edgeCells();

            double minX = chunkInfo.x() * edgeCells * voxelSize;
            double minY = chunkInfo.y() * edgeCells * voxelSize;
            double minZ = chunkInfo.z() * edgeCells * voxelSize;
            double maxX = minX + edgeCells * voxelSize;
            double maxY = minY + edgeCells * voxelSize;
            double maxZ = minZ + edgeCells * voxelSize;

            return repository.insertChunkIndex(
                    worldUuid, (short) chunkInfo.lod(),
                    chunkInfo.tx(), chunkInfo.ty(),
                    chunkInfo.x(), chunkInfo.y(), chunkInfo.z(),
                    edgeCells, voxelSize,
                    minX, minY, minZ, maxX, maxY, maxZ
            );

        } catch (DatabaseOperationException e) {
            throw e;

        } catch (IntegrityConstraintViolationException e) {
            // 동시 삽입 -> 재조회
            log.warn("청크 인덱스 이미 존재. 재조회: {}", chunkInfo);
            return repository.findChunkIndexUuid(
                    worldUuid,
                    (short) chunkInfo.lod(),
                    chunkInfo.tx(),
                    chunkInfo.ty(),
                    chunkInfo.x(),
                    chunkInfo.y(),
                    chunkInfo.z()
            ).orElseThrow(() -> new DatabaseOperationException(
                    DB_CONSTRAINT_VIOLATION,
                    "청크 인덱스 재조회 실패: " + chunkKey
            ));

        } catch (DataAccessException e) {
            log.error("DB 연결 실패. chunkKey: {}", chunkKey, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "DB 연결 실패: " + chunkKey,
                    e
            );

        } catch (Exception e) {
            log.error("청크 인덱스 생성 실패. chunkKey: {}", chunkKey, e);
            throw new DatabaseOperationException(
                    CHUNK_PROCESSING_FAILED,
                    "청크 인덱스 생성 실패: " + chunkKey,
                    e
            );
        }
    }

    public int getSnapshotVersion(UUID chunkUuid) {
        try {
            return repository.findMaxSnapshotVersion(chunkUuid).orElse(0);

        } catch (DataAccessException e) {
            log.error("스냅샷 버전 조회 실패. chunkUuid: {}", chunkUuid, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "스냅샷 버전 조회 실패: " + chunkUuid,
                    e
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public long getNextMeshVersion(UUID chunkUuid) {
        try {
            return repository.findMaxMeshVersion(chunkUuid).orElse(0L) + 1;

        } catch (DataAccessException e) {
            log.error("메시 버전 조회 실패. chunkUuid: {}", chunkUuid, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "메시 버전 조회 실패: " + chunkUuid,
                    e
            );
        }
    }


    public void saveSnapshotMetadata(
            UUID chunkUuid, SnapshotData data, int newVersion,
            SnapshotKind kind, boolean isCompacted
    ) {
        saveSnapshotMetadata(chunkUuid, data, newVersion, kind, isCompacted, Collections.emptyList());
    }

    @Transactional
    public void saveSnapshotMetadata(
            UUID chunkUuid, SnapshotData data, int newVersion,
            SnapshotKind kind, boolean isCompacted, List<ChunkInfo> child
            ) {
        try {
            // 스냅샷 저장
            UUID snapshotUuid = saveChunkSnapshot(
                    chunkUuid, newVersion, data.snapshotUrl(),
                    data.snapshotBytes(), data.finalSnapshot().size(),
                    kind
            );

            // 메시 저장
            long meshVersion = getNextMeshVersion(chunkUuid);
            UUID meshUuid = saveChunkMesh(
                    chunkUuid, snapshotUuid, meshVersion,
                    data.glbUrl(), data.glbBytes()
            );


            Instant lastWriteAt = Instant.now();
            // 청크 인덱스 업데이트
            updateChunkIndexAfterSnapshot(
                    chunkUuid, snapshotUuid, meshUuid,
                    newVersion, meshVersion, lastWriteAt, isCompacted
            );

            // child가 있는 경우 처리
            if (child != null && !child.isEmpty()) {
                repository.updateChildChunksCompactedAt(child, lastWriteAt);
            }

        } catch (DatabaseOperationException e) {
            throw e;

        } catch (DataAccessException e) {
            log.error("스냅샷 메타데이터 저장 실패. chunkUuid: {}", chunkUuid, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "스냅샷 메타데이터 저장 실패: " + chunkUuid,
                    e
            );

        } catch (Exception e) {
            log.error("스냅샷 메타데이터 저장 실패. chunkUuid: {}", chunkUuid, e);
            throw new DatabaseOperationException(
                    CHUNK_PROCESSING_FAILED,
                    "스냅샷 메타데이터 저장 실패: " + chunkUuid,
                    e
            );
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    protected UUID saveChunkSnapshot(UUID chunkUuid, long version, String storageUri,
                                     int compressedBytes, int nonEmptyCells, SnapshotKind kind) {
        try {
            Optional<UUID> existingSnapshot = repository.findSnapshotUuidByVersion(chunkUuid, version);

            if (existingSnapshot.isPresent()) {
                UUID existingUuid = existingSnapshot.get();
                log.info("스냅샷 이미 존재. UUID: {}, 버전: {}", existingUuid, version);

                repository.updateSnapshotUri(existingUuid, storageUri);
                return existingUuid;
            }

            UUID snapshotUuid = repository.insertChunkSnapshot(
                    chunkUuid, version, storageUri, compressedBytes, nonEmptyCells, kind
            );

            log.info("스냅샷 메타데이터 저장 완료. UUID: {}, 버전: {}", snapshotUuid, version);
            return snapshotUuid;

        } catch (IntegrityConstraintViolationException e) {
            // 버전 충돌
            log.warn("스냅샷 저장 실패 - 버전 충돌. Chunk: {}, Version: {}", chunkUuid, version, e);
            return repository.findSnapshotUuidByVersion(chunkUuid, version)
                    .orElseThrow(() -> new DatabaseOperationException(
                            DB_VERSION_CONFLICT,
                            "스냅샷 동시 삽입 후 재조회 실패: chunk=" + chunkUuid + ", version=" + version
                    ));

        } catch (DataAccessException e) {
            log.error("스냅샷 저장 DB 실패. Chunk: {}, Version: {}", chunkUuid, version, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "스냅샷 저장 DB 연결 실패: " + chunkUuid,
                    e
            );

        } catch (Exception e) {
            log.error("스냅샷 저장 실패. Chunk: {}, Version: {}", chunkUuid, version, e);
            throw new DatabaseOperationException(
                    CHUNK_PROCESSING_FAILED,
                    "스냅샷 저장 실패: " + chunkUuid,
                    e
            );
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    protected UUID saveChunkMesh(UUID chunkUuid, UUID snapshotUuid, long meshVersion,
                                 String artifactUri, int compressedBytes) {
        try {
            Optional<UUID> existingMesh = repository.findMeshUuidByVersion(chunkUuid, meshVersion);

            if (existingMesh.isPresent()) {
                UUID existingUuid = existingMesh.get();
                log.info("메시 이미 존재. UUID: {}, 버전: {}", existingUuid, meshVersion);

                repository.updateMeshUri(existingUuid, artifactUri);

                return existingUuid;
            }

            UUID meshUuid = repository.insertChunkMesh(
                    chunkUuid, snapshotUuid, meshVersion, artifactUri, compressedBytes
            );

            log.info("메쉬 메타데이터 저장 완료. UUID: {}, 버전: {}", meshUuid, meshVersion);
            return meshUuid;

        } catch (IntegrityConstraintViolationException e) {
            // 버전 충돌
            log.warn("메쉬 저장 실패 - 버전 충돌. Chunk: {}, Version: {}", chunkUuid, meshVersion, e);
            return repository.findMeshUuidByVersion(chunkUuid, meshVersion)
                    .orElseThrow(() -> new DatabaseOperationException(
                            DB_VERSION_CONFLICT,
                            "메시 동시 삽입 후 재조회 실패: chunk=" + chunkUuid + ", version=" + meshVersion
                    ));

        } catch (DataAccessException e) {
            log.error("메쉬 저장 DB 실패. Chunk: {}, Version: {}", chunkUuid, meshVersion, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "메시 저장 DB 연결 실패: " + chunkUuid,
                    e
            );

        } catch (Exception e) {
            log.error("메쉬 저장 실패. Chunk: {}, Version: {}", chunkUuid, meshVersion, e);
            throw new DatabaseOperationException(
                    CHUNK_PROCESSING_FAILED,
                    "메시 저장 실패: " + chunkUuid,
                    e
            );
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    protected void updateChunkIndexAfterSnapshot(UUID chunkUuid, UUID snapshotUuid, UUID meshUuid,
                                                 long version, long meshVersion, Instant lastWriteAt, boolean isCompacted) {
        try {
            int updatedRows = repository.updateChunkIndexAfterSnapshot(
                    chunkUuid, snapshotUuid, version, meshUuid, meshVersion, lastWriteAt, isCompacted
            );

            if (updatedRows == 0) {
                log.warn("청크 인덱스 업데이트 실패. UUID: {}", chunkUuid);
            } else {
                log.info("청크 인덱스 업데이트 완료. UUID: {}", chunkUuid);
            }

        } catch (DataAccessException e) {
            log.error("청크 인덱스 업데이트 DB 실패. UUID: {}", chunkUuid, e);
            throw new DatabaseOperationException(
                    DB_CONNECTION_FAILED,
                    "청크 인덱스 업데이트 DB 연결 실패: " + chunkUuid,
                    e
            );
        }
    }

}
