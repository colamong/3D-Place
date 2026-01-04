package com.colombus.snapshot.chunk.repo;

import com.colombus.common.domain.dto.ChunkInfo;
import com.colombus.snapshot.model.type.ArtifactKind;
import com.colombus.snapshot.model.type.SnapshotKind;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.colombus.snapshot.jooq.Tables.*;

@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    private final DSLContext dsl;

    public record WorldLodInfo(UUID uuid, int edgeCells, double voxelSizeM) {}

    public Optional<UUID> findWorldUuidByName(String worldName) {
        return dsl.select(WORLD.UUID)
                .from(WORLD)
                .where(WORLD.NAME.eq(worldName))
                .and(WORLD.DELETED_AT.isNull())
                .fetchOptional(WORLD.UUID);
    }

    public Optional<UUID> findChunkIndexUuid(UUID worldUuid, short lod, int tx, int ty, int x, int y, int z) {
        return dsl.select(CHUNK_INDEX.UUID)
                .from(CHUNK_INDEX)
                .where(CHUNK_INDEX.WORLD_ID.eq(worldUuid))
                .and(CHUNK_INDEX.LOD.eq(lod))
                .and(CHUNK_INDEX.TX.eq(tx))
                .and(CHUNK_INDEX.TY.eq(ty))
                .and(CHUNK_INDEX.IX.eq(x))
                .and(CHUNK_INDEX.IY.eq(y))
                .and(CHUNK_INDEX.IZ.eq(z))
                .and(CHUNK_INDEX.DELETED_AT.isNull())
                .fetchOptional(CHUNK_INDEX.UUID);
    }

    public Optional<WorldLodInfo> findWorldLodInfo(UUID worldUuid, short lod) {
        return dsl.select(
                WORLD_LOD.UUID,
                WORLD_LOD.CHUNK_EDGE_CELLS,
                WORLD_LOD.VOXEL_SIZE_M
        )
        .from(WORLD_LOD)
        .where(WORLD_LOD.WORLD_ID.eq(worldUuid))
        .and(WORLD_LOD.LOD.eq(lod))
        .and(WORLD_LOD.DELETED_AT.isNull())
        .fetchOptional(record -> new WorldLodInfo(
                record.value1(),
                record.value2(),
                record.value3()
        ));
    }


    public UUID insertChunkIndex(UUID worldUuid, short lod,
                                 int tx, int ty,
                                 int x, int y, int z,
                                 int edgeCells, double voxelSizeM,
                                 double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {
        UUID chunkUuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(CHUNK_INDEX)
                .set(CHUNK_INDEX.UUID, chunkUuid)
                .set(CHUNK_INDEX.WORLD_ID, worldUuid)
                .set(CHUNK_INDEX.LOD, lod)
                .set(CHUNK_INDEX.TX, tx)
                .set(CHUNK_INDEX.TY, ty)
                .set(CHUNK_INDEX.IX, x)
                .set(CHUNK_INDEX.IY, y)
                .set(CHUNK_INDEX.IZ, z)
                .set(CHUNK_INDEX.EDGE_CELLS, edgeCells)
                .set(CHUNK_INDEX.VOXEL_SIZE_M, voxelSizeM)
                .set(CHUNK_INDEX.AABB_MIN_X, minX)
                .set(CHUNK_INDEX.AABB_MIN_Y, minY)
                .set(CHUNK_INDEX.AABB_MIN_Z, minZ)
                .set(CHUNK_INDEX.AABB_MAX_X, maxX)
                .set(CHUNK_INDEX.AABB_MAX_Y, maxY)
                .set(CHUNK_INDEX.AABB_MAX_Z, maxZ)
                .set(CHUNK_INDEX.CURRENT_VERSION, 0L)
                .set(CHUNK_INDEX.CURRENT_MESH_VERSION, 0L)
                .set(CHUNK_INDEX.CREATED_AT, now)
                .set(CHUNK_INDEX.UPDATED_AT, now)
                .execute();

        return chunkUuid;
    }

    public Optional<Integer> findMaxSnapshotVersion(UUID chunkUuid) {
        Integer version = dsl.select(DSL.max(CHUNK_SNAPSHOT.VERSION))
                .from(CHUNK_SNAPSHOT)
                .where(CHUNK_SNAPSHOT.CHUNK_ID.eq(chunkUuid))
                .and(CHUNK_SNAPSHOT.DELETED_AT.isNull())
                .fetchOne(0, Integer.class);

        return Optional.ofNullable(version);
    }

    public Optional<Long> findMaxMeshVersion(UUID chunkUuid) {
        return dsl.select(DSL.max(CHUNK_MESH.MESH_VERSION))
                .from(CHUNK_MESH)
                .where(CHUNK_MESH.CHUNK_ID.eq(chunkUuid))
                .and(CHUNK_MESH.DELETED_AT.isNull())
                .fetchOptional(0, Long.class);
    }

    public UUID insertChunkSnapshot(UUID chunkUuid, long version, String storageUri,
                                    int compressedBytes, int nonEmptyCells, SnapshotKind kind) {
        UUID snapshotUuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(CHUNK_SNAPSHOT)
                .set(CHUNK_SNAPSHOT.UUID, snapshotUuid)
                .set(CHUNK_SNAPSHOT.CHUNK_ID, chunkUuid)
                .set(CHUNK_SNAPSHOT.VERSION, version)
                .set(CHUNK_SNAPSHOT.SCHEMA_VERSION, (short) 1)
                .set(CHUNK_SNAPSHOT.STORAGE_URI, storageUri)
                .set(CHUNK_SNAPSHOT.SNAPSHOT_KIND, kind)
                .set(CHUNK_SNAPSHOT.NON_EMPTY_CELLS, nonEmptyCells)
                .set(CHUNK_SNAPSHOT.COMPRESSED_BYTES, compressedBytes)
                .set(CHUNK_SNAPSHOT.CREATED_AT, now)
                .set(CHUNK_SNAPSHOT.UPDATED_AT, now)
                .execute();

        return snapshotUuid;
    }

    public UUID insertChunkMesh(UUID chunkUuid, UUID snapshotUuid, long meshVersion,
                                String artifactUri, int compressedBytes) {
        UUID meshUuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(CHUNK_MESH)
                .set(CHUNK_MESH.UUID, meshUuid)
                .set(CHUNK_MESH.CHUNK_ID, chunkUuid)
                .set(CHUNK_MESH.SNAPSHOT_ID, snapshotUuid)
                .set(CHUNK_MESH.MESH_VERSION, meshVersion)
                .set(CHUNK_MESH.ARTIFACT_URI, artifactUri)
                .set(CHUNK_MESH.ARTIFACT_KIND, ArtifactKind.GLB)
                .set(CHUNK_MESH.COMPRESSED_BYTES, compressedBytes)
                .set(CHUNK_MESH.CREATED_AT, now)
                .set(CHUNK_MESH.UPDATED_AT, now)
                .execute();

        return meshUuid;
    }

    public int updateChunkIndexAfterSnapshot(UUID chunkUuid, UUID snapshotUuid,
                                             long version, UUID meshUuid, long meshVersion,
                                             Instant lastWriteAt, boolean isCompacted) {
        OffsetDateTime writeAt = OffsetDateTime.ofInstant(lastWriteAt, ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        var update = dsl.update(CHUNK_INDEX)
                .set(CHUNK_INDEX.CURRENT_SNAPSHOT_ID, snapshotUuid)
                .set(CHUNK_INDEX.CURRENT_VERSION, version)
                .set(CHUNK_INDEX.CURRENT_MESH_ID, meshUuid)
                .set(CHUNK_INDEX.CURRENT_MESH_VERSION, meshVersion)
                .set(CHUNK_INDEX.LAST_WRITE_AT, writeAt)
                .set(CHUNK_INDEX.UPDATED_AT, now);

        if (isCompacted) {
            update = update.set(CHUNK_INDEX.LAST_COMPACTED_AT, writeAt);
        }

        return update
                .where(CHUNK_INDEX.UUID.eq(chunkUuid))
                .execute();
    }

    public Optional<UUID> findSnapshotUuidByVersion(UUID chunkUuid, long version) {
        return dsl.select(CHUNK_SNAPSHOT.UUID)
                .from(CHUNK_SNAPSHOT)
                .where(CHUNK_SNAPSHOT.CHUNK_ID.eq(chunkUuid))
                .and(CHUNK_SNAPSHOT.VERSION.eq(version))
                .and(CHUNK_SNAPSHOT.DELETED_AT.isNull())
                .fetchOptional(CHUNK_SNAPSHOT.UUID);
    }

    public Optional<UUID> findMeshUuidByVersion(UUID chunkUuid, long meshVersion) {
        return dsl.select(CHUNK_MESH.UUID)
                .from(CHUNK_MESH)
                .where(CHUNK_MESH.CHUNK_ID.eq(chunkUuid))
                .and(CHUNK_MESH.MESH_VERSION.eq(meshVersion))
                .and(CHUNK_MESH.DELETED_AT.isNull())
                .fetchOptional(CHUNK_MESH.UUID);
    }

    public int updateSnapshotUri(UUID snapshotUuid, String newUri) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return dsl.update(CHUNK_SNAPSHOT)
                .set(CHUNK_SNAPSHOT.STORAGE_URI, newUri)
                .set(CHUNK_SNAPSHOT.UPDATED_AT, now)
                .where(CHUNK_SNAPSHOT.UUID.eq(snapshotUuid))
                .execute();
    }

    public int updateMeshUri(UUID meshUuid, String newUri) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return dsl.update(CHUNK_MESH)
                .set(CHUNK_MESH.ARTIFACT_URI, newUri)
                .set(CHUNK_MESH.UPDATED_AT, now)
                .where(CHUNK_MESH.UUID.eq(meshUuid))
                .execute();
    }


    // lod2,3에 작업해야할 chunk 조회
    public List<ChunkInfo> findRecentlyUpdatedChunks(int lod) {
        return dsl.select(
                        CHUNK_INDEX.UUID,
                        WORLD.NAME,
                        CHUNK_INDEX.LOD,
                        CHUNK_INDEX.TX,
                        CHUNK_INDEX.TY,
                        CHUNK_INDEX.IX,
                        CHUNK_INDEX.IY,
                        CHUNK_INDEX.IZ
                )
                .from(CHUNK_INDEX)
                .join(WORLD).on(CHUNK_INDEX.WORLD_ID.eq(WORLD.UUID))
                .where(CHUNK_INDEX.LOD.eq((short) lod))
                .and(CHUNK_INDEX.DELETED_AT.isNull())
                .and(
                    CHUNK_INDEX.LAST_COMPACTED_AT.isNull()
                    .or(CHUNK_INDEX.LAST_WRITE_AT.greaterThan(CHUNK_INDEX.LAST_COMPACTED_AT))
                )
                .fetch()
                .map(record -> new ChunkInfo(
                        record.get(WORLD.NAME),
                        record.get(CHUNK_INDEX.LOD).intValue(),
                        record.get(CHUNK_INDEX.TX),
                        record.get(CHUNK_INDEX.TY),
                        record.get(CHUNK_INDEX.IX),
                        record.get(CHUNK_INDEX.IY),
                        record.get(CHUNK_INDEX.IZ)
                ));
    }

    public List<ChunkInfo> findExistingChunks(List<ChunkInfo> candidates, int lod) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        String worldName = candidates.get(0).worldName();

        // (tx, ty, x, y, z) 조합을 IN 절로 조회
        return dsl.select(
                        WORLD.NAME,
                        CHUNK_INDEX.LOD,
                        CHUNK_INDEX.TX,
                        CHUNK_INDEX.TY,
                        CHUNK_INDEX.IX,
                        CHUNK_INDEX.IY,
                        CHUNK_INDEX.IZ
                )
                .from(CHUNK_INDEX)
                .join(WORLD).on(CHUNK_INDEX.WORLD_ID.eq(WORLD.UUID))
                .where(WORLD.NAME.eq(worldName))
                .and(CHUNK_INDEX.LOD.eq((short) lod))
                .and(CHUNK_INDEX.CURRENT_VERSION.greaterThan(0L))  // 스냅샷 있음
                .and(CHUNK_INDEX.DELETED_AT.isNull())
                .and(createChunkCondition(candidates))  // IN 조건
                .fetch()
                .map(record -> new ChunkInfo(
                        record.get(WORLD.NAME),
                        record.get(CHUNK_INDEX.LOD).intValue(),
                        record.get(CHUNK_INDEX.TX),
                        record.get(CHUNK_INDEX.TY),
                        record.get(CHUNK_INDEX.IX),
                        record.get(CHUNK_INDEX.IY),
                        record.get(CHUNK_INDEX.IZ)
                ));
    }

    private Condition createChunkCondition(List<ChunkInfo> candidates) {
        // (tx, ty, x, y, z) IN ((0,0,-2,-2,0), (0,0,-2,-2,1), ...)
        return DSL.row(
                CHUNK_INDEX.TX,
                CHUNK_INDEX.TY,
                CHUNK_INDEX.IX,
                CHUNK_INDEX.IY,
                CHUNK_INDEX.IZ
        ).in(
                candidates.stream()
                        .map(c -> DSL.row(c.tx(), c.ty(), c.x(), c.y(), c.z()))
                        .collect(Collectors.toList())
        );
    }

    public int updateChildChunksCompactedAt(List<ChunkInfo> childChunks, Instant compactedAt) {
        if (childChunks == null || childChunks.isEmpty()) {
            return 0;
        }

        String worldName = childChunks.get(0).worldName();
        short lod = (short) childChunks.get(0).lod();
        OffsetDateTime compactedAtOffset = OffsetDateTime.ofInstant(compactedAt, ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // world_id 조회
        UUID worldUuid = dsl.select(WORLD.UUID)
                .from(WORLD)
                .where(WORLD.NAME.eq(worldName))
                .and(WORLD.DELETED_AT.isNull())
                .fetchOne(WORLD.UUID);

        if (worldUuid == null) {
            return 0;
        }

        return dsl.update(CHUNK_INDEX)
                .set(CHUNK_INDEX.LAST_COMPACTED_AT, compactedAtOffset)
                .set(CHUNK_INDEX.UPDATED_AT, now)
                .where(CHUNK_INDEX.WORLD_ID.eq(worldUuid))
                .and(CHUNK_INDEX.LOD.eq(lod))
                .and(CHUNK_INDEX.DELETED_AT.isNull())
                .and(createChunkCondition(childChunks))
                .execute();
    }
}
