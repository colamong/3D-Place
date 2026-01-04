package com.colombus.snapshot.util;

import com.colombus.snapshot.glb.model.Box;
import com.colombus.snapshot.glb.model.VoxelGeometry;
import com.colombus.snapshot.glb.model.VoxelGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


@Slf4j
public class GreedyMesher {
    private final float colorEpsilon;

    public GreedyMesher(float colorEpsilon) {
        this.colorEpsilon = colorEpsilon;
    }

    public List<Box> mesh(List<VoxelGeometry> voxels) {
        if (voxels.isEmpty()) {
            return Collections.emptyList();
        }

        // 복셀을 3D 그리드로 변환
        VoxelGrid grid = new VoxelGrid(voxels);

        List<Box> boxes = new ArrayList<>();
        boolean[][][] processed = new boolean[grid.getSizeX()][grid.getSizeY()][grid.getSizeZ()];

        for (int z = 0; z < grid.getSizeZ(); z++) {
            for (int y = 0; y < grid.getSizeY(); y++) {
                for (int x = 0; x < grid.getSizeX(); x++) {
                    if (processed[x][y][z] || !grid.hasVoxel(x, y, z)) {
                        continue;
                    }

                    VoxelGeometry voxel = grid.getVoxel(x, y, z);
                    Box box = createGreedyBox(grid, processed, x, y, z, voxel);
                    boxes.add(box);
                }
            }
        }

        log.debug("Greedy Meshing: {} voxels -> {} boxes ({}% reduction)",
                voxels.size(), boxes.size(),
                String.format("%.1f", (1 - boxes.size() / (double) voxels.size()) * 100));

        return boxes;
    }

    private Box createGreedyBox(VoxelGrid grid, boolean[][][] processed,
                                int startX, int startY, int startZ, VoxelGeometry startVoxel) {
        // X축 확장
        int endX = startX;
        while (endX < grid.getSizeX() &&
                !processed[endX][startY][startZ] &&
                grid.hasVoxel(endX, startY, startZ) &&
                colorMatches(grid.getVoxel(endX, startY, startZ), startVoxel)) {
            endX++;
        }

        // Y축 확장
        int endY = startY;
        outer: while (endY < grid.getSizeY()) {
            for (int x = startX; x < endX; x++) {
                if (processed[x][endY][startZ] ||
                        !grid.hasVoxel(x, endY, startZ) ||
                        !colorMatches(grid.getVoxel(x, endY, startZ), startVoxel)) {
                    break outer;
                }
            }
            endY++;
        }

        // Z축 확장
        int endZ = startZ;
        outer: while (endZ < grid.getSizeZ()) {
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    if (processed[x][y][endZ] ||
                            !grid.hasVoxel(x, y, endZ) ||
                            !colorMatches(grid.getVoxel(x, y, endZ), startVoxel)) {
                        break outer;
                    }
                }
            }
            endZ++;
        }

        // 영역을 처리됨으로 마킹
        for (int z = startZ; z < endZ; z++) {
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    processed[x][y][z] = true;
                }
            }
        }

        // 평균 색상 계산
        float avgR = 0, avgG = 0, avgB = 0;
        int count = 0;
        for (int z = startZ; z < endZ; z++) {
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    VoxelGeometry v = grid.getVoxel(x, y, z);
                    avgR += v.r();
                    avgG += v.g();
                    avgB += v.b();
                    count++;
                }
            }
        }
        avgR /= count;
        avgG /= count;
        avgB /= count;

        return new Box(
                grid.getMinX() + startX,
                grid.getMinY() + startY,
                grid.getMinZ() + startZ,
                grid.getMinX() + endX,
                grid.getMinY() + endY,
                grid.getMinZ() + endZ,
                avgR, avgG, avgB
        );
    }

    private boolean colorMatches(VoxelGeometry v1, VoxelGeometry v2) {
        return Math.abs(v1.r() - v2.r()) <= colorEpsilon &&
                Math.abs(v1.g() - v2.g()) <= colorEpsilon &&
                Math.abs(v1.b() - v2.b()) <= colorEpsilon;
    }
}