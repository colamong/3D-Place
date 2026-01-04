
package com.colombus.snapshot.model.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ArtifactKind {
    GLTF_DRACO("gltf+draco"),
    GLTF_MESHOPT("gltf+meshopt"),
    GLTF("gltf"),
    GLB("glb");

    private final String dbValue;

    public static ArtifactKind fromDbValue(String dbValue) {
        if (dbValue == null) return null;
        for (ArtifactKind kind : values()) {
            if (kind.dbValue.equals(dbValue)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown ArtifactKind: " + dbValue);
    }
}
