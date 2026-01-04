package com.colombus.snapshot.glb.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MeshBuffers {
    public final List<Float> positions = new ArrayList<>();
    public final List<Float> colors = new ArrayList<>();
    public final List<Integer> indices = new ArrayList<>();
    public int vertexCount = 0;

    private final Map<String, Integer> vertexCache = new HashMap<>();


    private void addVertexDirect(float x, float y, float z, float r, float g, float b) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        colors.add(r);
        colors.add(g);
        colors.add(b);
        vertexCount++;
    }

    public int addVertex(float x, float y, float z, float r, float g, float b) {
        // 정점의 고유 키 생성 (위치 + 색상)
        String key = makeVertexKey(x, y, z, r, g, b);

        // 이미 존재하는 정점이면 기존 인덱스 반환
        if (vertexCache.containsKey(key)) {
            return vertexCache.get(key);
        }

        // 새 정점 추가
        int index = vertexCount;
        addVertexDirect(x, y, z, r, g, b);
        vertexCache.put(key, index);

        return index;
    }

    public void addIndex(int index) {
        indices.add(index);
    }

    public void addQuad(float[] vertices, float r, float g, float b, int[] quadIndices) {
        if (vertices.length != 12) {
            throw new IllegalArgumentException("Quad를 생성하기 불충분한 데이터");
        }

        // 4개 정점의 인덱스 저장 (재사용 또는 새로 생성)
        int[] vertexIndices = new int[4];

        // 각 정점을 추가 (이미 존재하면 재사용)
        for (int i = 0; i < 4; i++) {
            float x = vertices[i * 3];
            float y = vertices[i * 3 + 1];
            float z = vertices[i * 3 + 2];

            // addVertex()가 자동으로 중복 체크 및 재사용 처리
            vertexIndices[i] = addVertex(x, y, z, r, g, b);
        }

        // 인덱스 추가 (2개의 삼각형)
        // quadIndices는 로컬 인덱스(0,1,2,3)이므로 실제 인덱스로 변환
        for (int localIdx : quadIndices) {
            addIndex(vertexIndices[localIdx]);
        }
    }

    private String makeVertexKey(float x, float y, float z, float r, float g, float b) {
        // 소수점 4자리로 반올림하여 부동소수점 오차 방지
        return String.format("%.4f,%.4f,%.4f,%.4f,%.4f,%.4f", x, y, z, r, g, b);
    }

    public VoxelBufferData toVoxelBufferData() {
        return new VoxelBufferData(
                new ArrayList<>(positions),
                new ArrayList<>(colors),
                new ArrayList<>(indices)
        );
    }

    public int getPositionCount() {
        return positions.size();
    }

    public int getColorCount() {
        return colors.size();
    }

    public int getIndexCount() {
        return indices.size();
    }
}