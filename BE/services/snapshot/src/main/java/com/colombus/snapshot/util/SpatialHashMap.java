package com.colombus.snapshot.util;

import com.colombus.snapshot.glb.model.Box;

import java.util.*;


public class SpatialHashMap {
    // 각 면의 위치를 키로 하는 맵
    private final Map<FaceKey, List<Box>> faceMap = new HashMap<>();


    public void addBox(Box box) {
        // 6개 면에 대한 키 생성 및 저장
        addFace(new FaceKey(box.x0, box.y0, box.y1, box.z0, box.z1, Direction.NEG_X), box);
        addFace(new FaceKey(box.x1, box.y0, box.y1, box.z0, box.z1, Direction.POS_X), box);
        addFace(new FaceKey(box.y0, box.x0, box.x1, box.z0, box.z1, Direction.NEG_Y), box);
        addFace(new FaceKey(box.y1, box.x0, box.x1, box.z0, box.z1, Direction.POS_Y), box);
        addFace(new FaceKey(box.z0, box.x0, box.x1, box.y0, box.y1, Direction.NEG_Z), box);
        addFace(new FaceKey(box.z1, box.x0, box.x1, box.y0, box.y1, Direction.POS_Z), box);
    }

    private void addFace(FaceKey key, Box box) {
        faceMap.computeIfAbsent(key, k -> new ArrayList<>()).add(box);
    }


    public boolean hasNeighbor(Box box, int dirX, int dirY, int dirZ) {
        FaceKey queryKey = createQueryKey(box, dirX, dirY, dirZ);
        if (queryKey == null) return false;

        List<Box> candidates = faceMap.get(queryKey);
        if (candidates == null || candidates.isEmpty()) return false;

        // 기하학적으로 정확히 인접한지 확인
        for (Box neighbor : candidates) {
            if (neighbor == box) continue; // 자기 자신 제외
            if (isGeometricallyAdjacent(box, neighbor, dirX, dirY, dirZ)) {
                return true;
            }
        }
        return false;
    }


    private FaceKey createQueryKey(Box box, int dirX, int dirY, int dirZ) {
        if (dirX == -1) return new FaceKey(box.x0, box.y0, box.y1, box.z0, box.z1, Direction.POS_X);
        if (dirX == +1) return new FaceKey(box.x1, box.y0, box.y1, box.z0, box.z1, Direction.NEG_X);
        if (dirY == -1) return new FaceKey(box.y0, box.x0, box.x1, box.z0, box.z1, Direction.POS_Y);
        if (dirY == +1) return new FaceKey(box.y1, box.x0, box.x1, box.z0, box.z1, Direction.NEG_Y);
        if (dirZ == -1) return new FaceKey(box.z0, box.x0, box.x1, box.y0, box.y1, Direction.POS_Z);
        if (dirZ == +1) return new FaceKey(box.z1, box.x0, box.x1, box.y0, box.y1, Direction.NEG_Z);
        return null;
    }


    private boolean isGeometricallyAdjacent(Box box, Box neighbor, int dirX, int dirY, int dirZ) {
        if (dirX == -1) {
            return neighbor.x1 == box.x0 &&
                   neighbor.y0 == box.y0 && neighbor.y1 == box.y1 &&
                   neighbor.z0 == box.z0 && neighbor.z1 == box.z1;
        }
        if (dirX == +1) {
            return neighbor.x0 == box.x1 &&
                   neighbor.y0 == box.y0 && neighbor.y1 == box.y1 &&
                   neighbor.z0 == box.z0 && neighbor.z1 == box.z1;
        }
        if (dirY == -1) {
            return neighbor.y1 == box.y0 &&
                   neighbor.x0 == box.x0 && neighbor.x1 == box.x1 &&
                   neighbor.z0 == box.z0 && neighbor.z1 == box.z1;
        }
        if (dirY == +1) {
            return neighbor.y0 == box.y1 &&
                   neighbor.x0 == box.x0 && neighbor.x1 == box.x1 &&
                   neighbor.z0 == box.z0 && neighbor.z1 == box.z1;
        }
        if (dirZ == -1) {
            return neighbor.z1 == box.z0 &&
                   neighbor.x0 == box.x0 && neighbor.x1 == box.x1 &&
                   neighbor.y0 == box.y0 && neighbor.y1 == box.y1;
        }
        if (dirZ == +1) {
            return neighbor.z0 == box.z1 &&
                   neighbor.x0 == box.x0 && neighbor.x1 == box.x1 &&
                   neighbor.y0 == box.y0 && neighbor.y1 == box.y1;
        }
        return false;
    }


    private static class FaceKey {
        final int position;
        final int u0, u1, v0, v1;
        final Direction dir;

        FaceKey(int position, int u0, int u1, int v0, int v1, Direction dir) {
            this.position = position;
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
            this.dir = dir;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FaceKey)) return false;
            FaceKey key = (FaceKey) o;
            return position == key.position &&
                   u0 == key.u0 && u1 == key.u1 &&
                   v0 == key.v0 && v1 == key.v1 &&
                   dir == key.dir;
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, u0, u1, v0, v1, dir);
        }
    }

    private enum Direction {
        NEG_X, POS_X, NEG_Y, POS_Y, NEG_Z, POS_Z
    }
}