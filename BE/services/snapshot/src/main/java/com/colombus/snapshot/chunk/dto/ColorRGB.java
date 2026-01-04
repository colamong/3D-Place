package com.colombus.snapshot.chunk.dto;

import static com.colombus.snapshot.glb.service.GLBGeneratorService.COLOR_NORMALIZE_FACTOR;
import static com.colombus.snapshot.glb.service.GLBGeneratorService.VOXEL_ID_MASK;

public record ColorRGB(float r, float g, float b) {
        public static ColorRGB fromBytes(byte[] colorBytes) {
            return new ColorRGB(
                    (colorBytes[0] & VOXEL_ID_MASK) / COLOR_NORMALIZE_FACTOR,
                    (colorBytes[1] & VOXEL_ID_MASK) / COLOR_NORMALIZE_FACTOR,
                    (colorBytes[2] & VOXEL_ID_MASK) / COLOR_NORMALIZE_FACTOR
            );
        }
    }