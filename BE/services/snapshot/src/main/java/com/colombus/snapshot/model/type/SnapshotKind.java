package com.colombus.snapshot.model.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SnapshotKind {
    MESH_SURFACE("mesh-surface"),
    SPARSE_VOXEL("sparse-voxel");

    private final String dbValue;

    public static SnapshotKind fromDbValue(String dbValue) {
        for (SnapshotKind kind : values()) {
            if (kind.dbValue.equals(dbValue)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown snapshot_kind: " + dbValue);
    }
}
