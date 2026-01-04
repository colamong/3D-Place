package com.colombus.snapshot.glb.service;

import com.colombus.common.domain.dto.DeltaDTO;
import com.colombus.snapshot.chunk.dto.ColorRGB;
import com.colombus.snapshot.chunk.dto.VoxelCoordinate;
import com.colombus.snapshot.exception.GlbUploadException;
import com.colombus.snapshot.glb.model.*;
import com.colombus.snapshot.util.GreedyMesher;
import com.colombus.snapshot.util.SpatialHashMap;
import de.javagl.jgltf.impl.v2.*;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.GltfModels;
import de.javagl.jgltf.model.io.GltfModelWriter;
import de.javagl.jgltf.model.io.v2.GltfAssetV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.colombus.snapshot.exception.SnapshotErrorCode.GLB_GENERATION_FAILED;
import static com.colombus.snapshot.glb.model.VoxelIndexTracker.BUFFER_VIEWS_PER_VOXEL;

@Service
@RequiredArgsConstructor
@Slf4j
// TODO: LOD별 Greedy-Meshing, Octree 등을 적용한 생성 메서드 구현 필요
public class GLBGeneratorService {

    // 복셀 하나의 크기
    private static final int VOXEL_SIZE = 1;

    // glTF 상수 정의
    private static final String GLTF_VERSION = "2.0";
    private static final String GLTF_GENERATOR = "3d-place-glb-generator";
    private static final int GLTF_ARRAY_BUFFER = 34962;  // 정점 데이터가 들어 있는 버퍼.
    private static final int GLTF_ELEMENT_ARRAY_BUFFER = 34963;  // 인덱스 데이터가 들어 있는 버퍼
    private static final int GLTF_FLOAT = 5126;  // 32비트 부동소수
    private static final int GLTF_UNSIGNED_INT = 5125;  // 부호 없는 32비트 정수

    // 복셀 큐브 정점 및 면 상수
    private static final int CUBE_VERTEX_COUNT = 8;
    private static final int CUBE_INDICES_COUNT = 36;  // 6 faces * 2 triangles * 3 vertices


    // 복셀 ID 비트 마스크 및 시프트
    public static final int VOXEL_ID_MASK = 0xFF;
    public static final int VOXEL_X_SHIFT = 16;
    public static final int VOXEL_Y_SHIFT = 8;

    // 색상 정규화
    public static final float COLOR_NORMALIZE_FACTOR = 255.0f;

    // 복셀 큐브의 8개 정점 인덱스
    private static final float[][] CUBE_VERTEX_OFFSETS = {
            {0, 0, 0}, // 0: 좌-앞-하
            {1, 0, 0}, // 1: 우-앞-하
            {1, 1, 0}, // 2: 우-뒤-하
            {0, 1, 0}, // 3: 좌-뒤-하
            {0, 0, 1}, // 4: 좌-앞-상
            {1, 0, 1}, // 5: 우-앞-상
            {1, 1, 1}, // 6: 우-뒤-상
            {0, 1, 1}  // 7: 좌-뒤-상
    };

    // 복셀 큐브의 6개 면 인덱스
    private static final int[][] CUBE_FACE_INDICES = {
            {0, 1, 5, 5, 4, 0},  // 앞면 (Front, -Y)
            {2, 3, 7, 7, 6, 2},  // 뒷면 (Back, +Y)
            {0, 3, 2, 2, 1, 0},  // 아랫면 (Bottom, -Z)
            {4, 5, 6, 6, 7, 4},  // 윗면 (Top, +Z)
            {3, 0, 4, 4, 7, 3},  // 왼쪽면 (Left, -X)
            {1, 2, 6, 6, 5, 1}   // 오른쪽면 (Right, +X)
    };

    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    public byte[] generateGLBWithSeparateMeshes(List<DeltaDTO> deltas) {
        try {
            log.debug("GLB 생성 시작. Delta 수: {}", deltas.size());

            GlTF gltf = new GlTF();
            gltf.setAsset(createAsset());

            Scene scene = new Scene();
            gltf.addScenes(scene);
            gltf.setScene(0);

            List<ByteBuffer> allBuffers = new ArrayList<>();
            VoxelIndexTracker indexTracker = new VoxelIndexTracker();

            for (DeltaDTO delta : deltas) {
                VoxelGeometry geometry = createVoxelGeometry(delta);
                VoxelBufferData bufferData = createVoxelBufferData(geometry);
                ByteBuffer voxelBuffer = createVoxelBufferFromData(bufferData);
                allBuffers.add(voxelBuffer);

                addVoxelToGltf(gltf, scene, bufferData, delta, indexTracker);
            }

            ByteBuffer combinedBuffer = combineBuffers(allBuffers, gltf);
            byte[] glbData = generateGLBFile(gltf, combinedBuffer);

            log.info("GLB 생성 완료. 크기: {}KB, Delta 수: {}", glbData.length / 1024, deltas.size());
            return glbData;

        } catch (GlbUploadException e) {
            throw e;
        } catch (Exception e) {
            log.error("GLB 생성 중 예상치 못한 실패. Delta 수: {}", deltas.size(), e);
            throw new GlbUploadException(GLB_GENERATION_FAILED,
                    "GLB 생성 실패: " + e.getMessage(), e);
        }
    }

     // 좌표 추출 로직 단순화
    private VoxelGeometry createVoxelGeometry(DeltaDTO delta) {
        VoxelCoordinate coord = VoxelCoordinate.fromDelta(delta);
        ColorRGB color = ColorRGB.fromBytes(delta.colorBytes());

        return new VoxelGeometry(
                coord.x() * VOXEL_SIZE,
                coord.y() * VOXEL_SIZE,
                coord.z() * VOXEL_SIZE,
                color.r(), color.g(), color.b()
        );
    }

    private VoxelBufferData createVoxelBufferData(VoxelGeometry geometry) {
        List<Float> positions = new ArrayList<>();
        List<Float> colors = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // 8개의 큐브 정점 생성
        for (float[] offset : CUBE_VERTEX_OFFSETS) {
            positions.add(geometry.x() + offset[0] * VOXEL_SIZE);
            positions.add(geometry.y() + offset[1] * VOXEL_SIZE);
            positions.add(geometry.z() + offset[2] * VOXEL_SIZE);

            colors.add(geometry.r());
            colors.add(geometry.g());
            colors.add(geometry.b());
        }

        // 6개 면의 인덱스 데이터 생성
        for (int[] face : CUBE_FACE_INDICES) {
            for (int idx : face) {
                indices.add(idx);
            }
        }

        return new VoxelBufferData(positions, colors, indices);
    }

     // VoxelBufferData에서 ByteBuffer 생성
    private ByteBuffer createVoxelBufferFromData(VoxelBufferData bufferData) {
        try {
            ByteBuffer positionsBuffer = createFloatBuffer(bufferData.positions());
            ByteBuffer colorsBuffer = createFloatBuffer(bufferData.colors());
            ByteBuffer indicesBuffer = createIntBuffer(bufferData.indices());

            int totalBytes = positionsBuffer.capacity() +
                    colorsBuffer.capacity() +
                    indicesBuffer.capacity();

            ByteBuffer combined = ByteBuffer.allocate(totalBytes)
                    .order(ByteOrder.LITTLE_ENDIAN);
            combined.put(positionsBuffer)
                    .put(colorsBuffer)
                    .put(indicesBuffer);

            return combined.flip();
        } catch (Exception e) {
            log.error("복셀 버퍼 생성 실패", e);
            throw new GlbUploadException(GLB_GENERATION_FAILED,
                    "복셀 버퍼 생성 실패", e);
        }
    }

    private void addVoxelToGltf(GlTF gltf, Scene scene, VoxelBufferData bufferData,
                                DeltaDTO delta, VoxelIndexTracker tracker) {
        int positionsBytes = bufferData.positions().size() * Float.BYTES;
        int colorsBytes = bufferData.colors().size() * Float.BYTES;
        int indicesBytes = bufferData.indices().size() * Integer.BYTES;

        // BufferView 생성
        addBufferView(gltf, 0, positionsBytes, GLTF_ARRAY_BUFFER);
        addBufferView(gltf, positionsBytes, colorsBytes, GLTF_ARRAY_BUFFER);
        addBufferView(gltf, positionsBytes + colorsBytes, indicesBytes,
                GLTF_ELEMENT_ARRAY_BUFFER);

        // Accessor 생성
        addAccessor(gltf, tracker.bufferViewIndex, GLTF_FLOAT,
                CUBE_VERTEX_COUNT, "VEC3");
        addAccessor(gltf, tracker.bufferViewIndex + 1, GLTF_FLOAT,
                CUBE_VERTEX_COUNT, "VEC3");
        addAccessor(gltf, tracker.bufferViewIndex + 2, GLTF_UNSIGNED_INT,
                CUBE_INDICES_COUNT, "SCALAR");

        // Mesh와 Node 생성
        MeshPrimitive primitive = createMeshPrimitive(tracker.accessorIndex);
        Mesh mesh = createMesh(primitive, delta);
        gltf.addMeshes(mesh);

        // Node 생성 및 메타데이터 추가
        Node node = createNode(tracker.meshIndex, delta);
        gltf.addNodes(node);
        scene.addNodes(tracker.nodeIndex);

        tracker.increment();
    }

    private MeshPrimitive createMeshPrimitive(int accessorIndex) {
        MeshPrimitive primitive = new MeshPrimitive();
        primitive.setIndices(accessorIndex + 2);
        primitive.addAttributes("POSITION", accessorIndex);
        primitive.addAttributes("COLOR_0", accessorIndex + 1);

        return primitive;
    }

    // Mesh 생성
    private Mesh createMesh(MeshPrimitive primitive, DeltaDTO delta) {
        Mesh mesh = new Mesh();
        mesh.setName("voxel_" + delta.opId().toString());
        mesh.addPrimitives(primitive);
        return mesh;
    }

    // Node 생성 및 메타데이터 설정
    private Node createNode(int meshIndex, DeltaDTO delta) {
        Node node = new Node();
        node.setMesh(meshIndex);
        node.setName("node_voxel_" + delta.opId().toString());

        // 복셀의 메타 정보를 node의 extras에 저장
        Map<String, Object> deltaMeta = new HashMap<>();
        deltaMeta.put("opId", delta.opId());
        deltaMeta.put("vSeq", delta.vSeq());
        deltaMeta.put("actor", delta.actor());
        node.setExtras(deltaMeta);

        return node;
    }

    // 모든 복셀 버퍼를 하나로 결합하고 BufferView의 offset 조정
    private ByteBuffer combineBuffers(List<ByteBuffer> allBuffers, GlTF gltf) {
        try {
            int totalBufferSize = allBuffers.stream()
                    .mapToInt(ByteBuffer::capacity)
                    .sum();

            ByteBuffer combined = ByteBuffer.allocate(totalBufferSize)
                    .order(ByteOrder.LITTLE_ENDIAN);

            int currentOffset = 0;
            for (int i = 0; i < allBuffers.size(); i++) {
                ByteBuffer voxelBuffer = allBuffers.get(i);
                voxelBuffer.rewind();
                combined.put(voxelBuffer);

                // BufferView offset 조정
                int bufferViewStartIndex = i * BUFFER_VIEWS_PER_VOXEL;
                for (int j = 0; j < BUFFER_VIEWS_PER_VOXEL; j++) {
                    BufferView bv = gltf.getBufferViews()
                            .get(bufferViewStartIndex + j);
                    bv.setByteOffset(currentOffset + bv.getByteOffset());
                }
                currentOffset += voxelBuffer.capacity();
            }

            return combined.flip();
        } catch (Exception e) {
            log.error("버퍼 결합 실패", e);
            throw new GlbUploadException(GLB_GENERATION_FAILED, "버퍼 결합 실패", e);
        }
    }

    // 최종 GLB 파일 생성
    private byte[] generateGLBFile(GlTF gltf, ByteBuffer combinedBuffer) {
        try {
            Buffer singleBuffer = new Buffer();
            singleBuffer.setByteLength(combinedBuffer.capacity());
            gltf.setBuffers(List.of(singleBuffer));

            GltfAssetV2 gltfAsset = new GltfAssetV2(gltf, combinedBuffer);
            GltfModel gltfModel = GltfModels.create(gltfAsset);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new GltfModelWriter().writeBinary(gltfModel, outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("GLB 파일 쓰기 실패", e);
            throw new GlbUploadException(GLB_GENERATION_FAILED,
                    "GLB 파일 쓰기 실패", e);
        } catch (Exception e) {
            log.error("GLB 파일 생성 실패", e);
            throw new GlbUploadException(GLB_GENERATION_FAILED,
                    "GLB 파일 생성 실패", e);
        }
    }


    public byte[] generateLODGLBWithGreedyMeshing(List<VoxelGeometry> deltas, int lod) {
        try {
            log.info("LOD {} Greedy Meshing GLB 생성 시작. Delta 수: {}", lod, deltas.size());
            long startTime = System.currentTimeMillis();


            if (deltas.isEmpty()) {
                log.warn("LOD {} 적용 후 복셀이 없습니다.", lod);
                return createEmptyGLB();
            }

            // 최소 좌표 계산
            int minX = deltas.stream().mapToInt(VoxelGeometry::x).min().orElse(0);
            int minY = deltas.stream().mapToInt(VoxelGeometry::y).min().orElse(0);
            int minZ = deltas.stream().mapToInt(VoxelGeometry::z).min().orElse(0);

            // Greedy Meshing
            GreedyMesher mesher = new GreedyMesher(0.01f);
            List<Box> boxes = mesher.mesh(deltas);

            log.info("LOD {}: {} 복셀 -> {} 박스로 병합 ({}% 감소)",
                    lod, deltas.size(), boxes.size(),
                    String.format("%.1f", (1 - boxes.size() / (double) deltas.size()) * 100));

            // 외부면 추출
            SpatialHashMap spatialMap = new SpatialHashMap();
            boxes.forEach(spatialMap::addBox);

            MeshBuffers buffers = createMeshBuffersForBoxes(boxes, spatialMap,
                    lod, minX, minY, minZ);
            VoxelBufferData bufferData = buffers.toVoxelBufferData();

            log.info("LOD {}: {} 박스 -> 정점 {} 개, 삼각형 {} 개",
                    lod, boxes.size(), buffers.vertexCount,
                    buffers.getIndexCount() / 3);

            byte[] glbData = createGLB(bufferData, buffers.vertexCount, boxes,
                    deltas, lod, minX, minY, minZ);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("LOD {} GLB 생성 완료. 크기: {}KB, 처리 시간: {}ms",
                    lod, glbData.length / 1024, elapsed);

            return glbData;

        } catch (GlbUploadException e) {
            throw e;
        } catch (Exception e) {
            log.error("LOD {} GLB 생성 실패", lod, e);
            throw new GlbUploadException(GLB_GENERATION_FAILED,
                    "LOD GLB 생성 실패: " + e.getMessage(), e);
        }
    }

    private MeshBuffers createMeshBuffersForBoxes(List<Box> boxes,
                                                  SpatialHashMap spatialMap,
                                                  int lod, int originX,
                                                  int originY, int originZ) {
        MeshBuffers buffers = new MeshBuffers();

        for (Box box : boxes) {
            float scale = VOXEL_SIZE * lod;

            // 상대 좌표로 변환
            float x0 = (box.x0 - originX) * scale;
            float y0 = (box.y0 - originY) * scale;
            float z0 = (box.z0 - originZ) * scale;
            float x1 = (box.x1 - originX) * scale;
            float y1 = (box.y1 - originY) * scale;
            float z1 = (box.z1 - originZ) * scale;

            // 6개 면 중 외부면만 추가
            addFaceIfVisible(buffers, box, spatialMap, -1, 0, 0,
                    new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0});
            addFaceIfVisible(buffers, box, spatialMap, +1, 0, 0,
                    new float[]{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1});
            addFaceIfVisible(buffers, box, spatialMap, 0, -1, 0,
                    new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1});
            addFaceIfVisible(buffers, box, spatialMap, 0, +1, 0,
                    new float[]{x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0});
            addFaceIfVisible(buffers, box, spatialMap, 0, 0, -1,
                    new float[]{x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0});
            addFaceIfVisible(buffers, box, spatialMap, 0, 0, +1,
                    new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1});
        }

        return buffers;
    }

    // 면 추가
    private void addFaceIfVisible(MeshBuffers buffers, Box box,
                                  SpatialHashMap spatialMap,
                                  int dx, int dy, int dz, float[] vertices) {
        if (!spatialMap.hasNeighbor(box, dx, dy, dz)) {
            buffers.addQuad(vertices, box.r, box.g, box.b, QUAD_INDICES);
        }
    }

    private byte[] createGLB(VoxelBufferData bufferData, int vertexCount,
                             List<Box> boxes, List<VoxelGeometry> originalDeltas,
                             int lod, int originX, int originY, int originZ)
            throws IOException {

        // ByteBuffer 생성
        int posBytes = bufferData.positions().size() * Float.BYTES;
        int colBytes = bufferData.colors().size() * Float.BYTES;
        int idxBytes = bufferData.indices().size() * Integer.BYTES;

        ByteBuffer combined = ByteBuffer.allocate(posBytes + colBytes + idxBytes)
                .order(ByteOrder.LITTLE_ENDIAN);

        bufferData.positions().forEach(combined::putFloat);
        bufferData.colors().forEach(combined::putFloat);
        bufferData.indices().forEach(combined::putInt);
        combined.flip();

        // glTF 구조 생성
        GlTF gltf = new GlTF();
        gltf.setAsset(createAsset());

        Scene scene = new Scene();
        gltf.addScenes(scene);
        gltf.setScene(0);

        // Node와 메타데이터
        Node node = createLODNode(lod, boxes, originalDeltas, vertexCount,
                bufferData, originX, originY, originZ);
        gltf.addNodes(node);
        scene.addNodes(0);

        // Buffer + BufferViews + Accessors
        setupGLTFStructure(gltf, combined, posBytes, colBytes, idxBytes,
                vertexCount, bufferData.indices().size());

        // Mesh + Primitive
        MeshPrimitive prim = new MeshPrimitive();
        prim.setIndices(2);
        prim.addAttributes("POSITION", 0);
        prim.addAttributes("COLOR_0", 1);

        Mesh mesh = new Mesh();
        mesh.setName("voxel_greedy_lod_" + lod);
        mesh.addPrimitives(prim);
        gltf.addMeshes(mesh);

        // GLB 생성
        return writeGLBToBytes(gltf, combined);
    }

    // LOD Node 생성
    private Node createLODNode(int lod, List<Box> boxes,
                               List<VoxelGeometry> originalDeltas, int vertexCount,
                               VoxelBufferData bufferData,
                               int originX, int originY, int originZ) {
        Node node = new Node();
        node.setMesh(0);
        node.setName("voxel_lod_" + lod);

        Map<String, Object> extras = new HashMap<>();
        extras.put("lod", lod);
        extras.put("boxCount", boxes.size());
        extras.put("originalDeltaCount", originalDeltas.size());
        extras.put("vertexCount", vertexCount);
        extras.put("triangleCount", bufferData.indices().size() / 3);

        // 기준점 정보
        int scale = VOXEL_SIZE * lod;
        Map<String, Object> origin = new HashMap<>();
        origin.put("x", originX * scale);
        origin.put("y", originY * scale);
        origin.put("z", originZ * scale);
        extras.put("origin", origin);

        Map<String, Object> originIndex = new HashMap<>();
        originIndex.put("x", originX);
        originIndex.put("y", originY);
        originIndex.put("z", originZ);
        extras.put("originIndex", originIndex);

        node.setExtras(extras);
        return node;
    }

    // glTF 구조 설정
    private void setupGLTFStructure(GlTF gltf, ByteBuffer combined,
                                    int posBytes, int colBytes, int idxBytes,
                                    int vertexCount, int indexCount) {
        // Buffer
        Buffer buffer = new Buffer();
        buffer.setByteLength(combined.capacity());
        gltf.setBuffers(List.of(buffer));

        // BufferViews
        addBufferView(gltf, 0, posBytes, GLTF_ARRAY_BUFFER);
        addBufferView(gltf, posBytes, colBytes, GLTF_ARRAY_BUFFER);
        addBufferView(gltf, posBytes + colBytes, idxBytes, GLTF_ELEMENT_ARRAY_BUFFER);

        // Accessors
        addAccessor(gltf, 0, GLTF_FLOAT, vertexCount, "VEC3");
        addAccessor(gltf, 1, GLTF_FLOAT, vertexCount, "VEC3");
        addAccessor(gltf, 2, GLTF_UNSIGNED_INT, indexCount, "SCALAR");
    }

    // GLB 파일 쓰기
    private byte[] writeGLBToBytes(GlTF gltf, ByteBuffer combined) throws IOException {
        GltfAssetV2 gltfAsset = new GltfAssetV2(gltf, combined);
        GltfModel gltfModel = GltfModels.create(gltfAsset);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new GltfModelWriter().writeBinary(gltfModel, outputStream);
        return outputStream.toByteArray();
    }

    private byte[] createEmptyGLB() throws IOException {
        GlTF gltf = new GlTF();
        gltf.setAsset(createAsset());

        Scene scene = new Scene();
        gltf.addScenes(scene);
        gltf.setScene(0);

        return writeGLBToBytes(gltf, ByteBuffer.allocate(0));
    }

    private ByteBuffer createFloatBuffer(List<Float> data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.size() * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        data.forEach(buffer::putFloat);
        return buffer.flip();
    }

    private ByteBuffer createIntBuffer(List<Integer> data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.size() * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        data.forEach(buffer::putInt);
        return buffer.flip();
    }

    // BufferView 추가
    private void addBufferView(GlTF gltf, int byteOffset, int byteLength, int target) {
        BufferView bufferView = new BufferView();
        bufferView.setBuffer(0);
        bufferView.setByteOffset(byteOffset);
        bufferView.setByteLength(byteLength);
        bufferView.setTarget(target);
        gltf.addBufferViews(bufferView);
    }

    //Accessor 추가
    private void addAccessor(GlTF gltf, int bufferViewIndex, int componentType,
                             int count, String type) {
        Accessor accessor = new Accessor();
        accessor.setBufferView(bufferViewIndex);
        accessor.setComponentType(componentType);
        accessor.setCount(count);
        accessor.setType(type);
        gltf.addAccessors(accessor);
    }

    // Asset 생성
    private Asset createAsset() {
        Asset asset = new Asset();
        asset.setVersion(GLTF_VERSION);
        asset.setGenerator(GLTF_GENERATOR);
        return asset;
    }
}
