// src/features/voxels/hooks/useChunkLoader.ts
import { useEffect, useRef, useState } from 'react';
import type { Viewer } from 'cesium';
import { WebIO } from '@gltf-transform/core';
import { 
  getVisibleChunks, 
  chunkIdToString,
  getCameraChunkIndices,
  type CameraChunkInfo
} from '@/features/voxels/utils/chunkLoader';
import { fetchChunkData } from '@/api/world';
import { centerFromChunkIndices } from '@/features/voxels/chunk';
import type { GetTileBaseHeight } from '@/features/voxels/types';
import { useVoxelStateStore } from '@/stores/useVoxelStateStore';
import { useChunkModelStore } from '@/stores/useChunkModelStore';
import type { ChunkModelSpec } from '@/features/voxels/types';
import { VOXEL_SIZE_M } from '@/features/voxels/constants';
import { ZOOM, CHUNK_N } from '@/features/voxels/constants';

export function useChunkLoader(
  viewer: Viewer | null,
  getTileBaseHeight: GetTileBaseHeight,
  enabled: boolean = true
) {
  const loadedChunksRef = useRef<Set<string>>(new Set());
  const pushVoxelsBatch = useVoxelStateStore((s) => s.pushVoxelsBatch);
  const eraseVoxelById = useVoxelStateStore((s) => s.eraseVoxelById);
  const upsertModel = useChunkModelStore((s) => s.upsertItem);
  const clearModels = useChunkModelStore((s) => s.clear);
  
  // 현재 카메라 정보와 LOD 추적
  const [cameraChunkInfo, setCameraChunkInfo] = useState<CameraChunkInfo | null>(null);
  const currentLODRef = useRef<number>(0);
  
  useEffect(() => {
    if (!viewer || !enabled) return;
    
    let mounted = true;
    
    const loadChunks = async () => {
      if (!mounted) return;
      
      // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      // 1️⃣ 카메라 정보 파악 (ZOOM=9 고정, LOD 동적)
      // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      const cameraInfo = getCameraChunkIndices(viewer, getTileBaseHeight);
      if (cameraInfo) {
        setCameraChunkInfo(cameraInfo);
        
        console.log('[useChunkLoader] 카메라 위치:', {
          tile: `(${cameraInfo.tx}, ${cameraInfo.ty})`,
          chunk: `(${cameraInfo.cx}, ${cameraInfo.cy}, ${cameraInfo.cz})`,
          heightAboveGround: cameraInfo.heightAboveGround.toFixed(2),
          currentLOD: cameraInfo.currentLOD,
        });
        
        // ⚠️ LOD 변경 감지 → 캐시 초기화
        if (cameraInfo.currentLOD !== currentLODRef.current) {
          console.log(
            `[useChunkLoader] ⚠️ LOD 변경: ${currentLODRef.current} → ${cameraInfo.currentLOD}`
          );
          currentLODRef.current = cameraInfo.currentLOD;
          loadedChunksRef.current.clear(); // LOD 변경 시 캐시 초기화
          try { clearModels(); } catch {}
        }
      }
      
      // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      // 2️⃣ 현재 LOD로 보이는 청크 계산
      // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      const WORLD = 'world'; // ✅ 고정
      const activeLOD = currentLODRef.current;
      
      const chunks = getVisibleChunks(
        viewer,
        getTileBaseHeight,
        activeLOD, // ✅ 현재 LOD로 청크 조회
        WORLD
      );
      
      console.log(
        `[useChunkLoader] 보이는 청크 (ZOOM=9, LOD=${activeLOD}):`,
        chunks.length,
        chunks.map(chunkIdToString)
      );
      
      // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      // 3️⃣ 청크 데이터 로드
      // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      for (const chunk of chunks) {
        const chunkKey = chunkIdToString(chunk);
        
        // 이미 로드된 청크는 스킵
        if (loadedChunksRef.current.has(chunkKey)) {
          continue;
        }
        
        console.log(
          `[useChunkLoader] 로딩 시작: ${chunk.world}/l${chunk.lod}/${chunkKey}`
        );
        
        const data = await fetchChunkData(chunk.world, chunk.lod, chunkKey);
        
        if (!mounted) break;
        
        if (data) {
          console.log('[useChunkLoader] 로드 완료:', chunkKey, data);

          loadedChunksRef.current.add(chunkKey);

          // ── LOD별 처리 분기 ───────────────────────────────────────────
          if (data.glbUrl) {
            if (activeLOD === 0) {
              // LOD0: 기존대로 GLB를 보셀로 변환 (상호작용 경로)
              try {
                const io = new WebIO();
                const glb = await io.read(data.glbUrl);
                const nodes = glb.getRoot().listNodes();
                console.log('[useChunkLoader] GLB 노드 개수:', nodes.length);
                const voxelsToAdd: any[] = [];
                for (const node of nodes) {
                  if (!mounted) break;
                  const name = node.getName?.() ?? '';
                  if (!name.startsWith('node_voxel_')) continue;
                  const extras = node.getExtras?.();
                  const mesh = node.getMesh?.();
                  if (!mesh) continue;
                  const prim = mesh.listPrimitives()[0];
                  if (!prim) continue;
                  const pos = prim.getAttribute('POSITION')?.getArray() as Float32Array | undefined;
                  const col = prim.getAttribute('COLOR_0')?.getArray() as Float32Array | undefined;
                  if (!pos || pos.length < 3) continue;
                  let minX = Infinity, maxX = -Infinity;
                  let minY = Infinity, maxY = -Infinity;
                  let minZ = Infinity, maxZ = -Infinity;
                  for (let i = 0; i < pos.length; i += 3) {
                    const x = pos[i], y = pos[i + 1], z = pos[i + 2];
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                  }
                  const lx = Math.round((minX + maxX) * 0.5 - 0.5);
                  const ly = Math.round((minY + maxY) * 0.5 - 0.5);
                  const lz = Math.round((minZ + maxZ) * 0.5 - 0.5);
                  const center = centerFromChunkIndices({
                    tile: { x: chunk.tx, y: chunk.ty },
                    chunk: { cx: chunk.cx, cy: chunk.cy, ck: chunk.cz },
                    local: { lx, ly, lk: lz },
                    getTileBaseHeight,
                    zoom: ZOOM,
                  });
                  let r = 255, g = 255, b = 255;
                  if (col && col.length >= 3) {
                    let rSum = 0, gSum = 0, bSum = 0, n = 0;
                    for (let i = 0; i < col.length; i += 3) {
                      rSum += col[i]; gSum += col[i + 1]; bSum += col[i + 2]; n++;
                    }
                    r = Math.round((rSum / n) * 255);
                    g = Math.round((gSum / n) * 255);
                    b = Math.round((bSum / n) * 255);
                  }
                  const id = `${ZOOM}/${chunk.tx}/${chunk.ty}/${chunk.cx}/${chunk.cy}/${chunk.cz}/${lx}/${ly}/${lz}`;
                  const opId = extras?.opId ?? null;
                  const vSeq = extras?.vSeq ?? null;
                  voxelsToAdd.push({ id, z: ZOOM, tx: chunk.tx, ty: chunk.ty, cx: chunk.cx, cy: chunk.cy, cz: chunk.cz, vx: lx, vy: ly, vz: lz, center, r, g, b, opId, vSeq });
                }
                console.log('[useChunkLoader] 파싱된 보셀 개수:', voxelsToAdd.length);
                if (mounted && voxelsToAdd.length > 0) {
                  pushVoxelsBatch(voxelsToAdd);
                  console.log('[useChunkLoader] ✅ GLB→보셀 완료:', voxelsToAdd.length);
                }
              } catch (err) {
                console.error('[useChunkLoader] ❌ GLB 파싱 실패:', err);
              }
            } else {
              // LOD1+ : 렌더 전용 Model을 앵커(코너) 기준으로 배치
              const unit = activeLOD === 2 ? VOXEL_SIZE_M * 2 : activeLOD === 3 ? VOXEL_SIZE_M * 4 : VOXEL_SIZE_M;
              const spec: ChunkModelSpec = {
                id: `${chunk.world}/l${activeLOD}/${chunkKey}`,
                url: data.glbUrl,
                tile: { x: chunk.tx, y: chunk.ty },
                chunk: { cx: chunk.cx, cy: chunk.cy, ck: chunk.cz },
                zoom: ZOOM,
                origin: 'corner',
                unitScale: unit,
                allowPicking: false,
                debug: false,
              };
              try { upsertModel(spec); } catch {}
            }
          }
          
          // ┌─────────────────────────────────────────────────────────────┐
          // │ 🎨 Paint 델타 적용                                          │
          // └─────────────────────────────────────────────────────────────┘
          if (data.paintResponses && data.paintResponses.length > 0) {
            console.log('[useChunkLoader] Paint 델타:', data.paintResponses.length);
            
            const voxels = data.paintResponses.map((paint) => {
              const colorBytes = atob(paint.colorBytes);
              const r = colorBytes.charCodeAt(0);
              const g = colorBytes.charCodeAt(1);
              const b = colorBytes.charCodeAt(2);
              
              const center = centerFromChunkIndices({
                tile: { x: paint.chunkIndex.tx, y: paint.chunkIndex.ty },
                chunk: {
                  cx: paint.chunkIndex.cix,
                  cy: paint.chunkIndex.ciy,
                  ck: paint.chunkIndex.ciz,
                },
                local: {
                  lx: paint.voxelIndex.vix,
                  ly: paint.voxelIndex.viy,
                  lk: paint.voxelIndex.viz,
                },
                getTileBaseHeight,
                zoom: ZOOM,
              });
              
              return {
                id: paint.opId,
                z: ZOOM,
                tx: paint.chunkIndex.tx,
                ty: paint.chunkIndex.ty,
                cx: paint.chunkIndex.cix,
                cy: paint.chunkIndex.ciy,
                cz: paint.chunkIndex.ciz,
                vx: paint.voxelIndex.vix,
                vy: paint.voxelIndex.viy,
                vz: paint.voxelIndex.viz,
                center,
                r,
                g,
                b,
                opId: paint.opId,
                vSeq: paint.vSeq,
              };
            });
            
            pushVoxelsBatch(voxels);
            console.log('[useChunkLoader] ✅ Paint 델타 적용:', voxels.length);
          }
          
          // ┌─────────────────────────────────────────────────────────────┐
          // │ 🗑️ Erase 델타 적용                                          │
          // └─────────────────────────────────────────────────────────────┘
          if (data.eraseResponses && data.eraseResponses.length > 0) {
            console.log('[useChunkLoader] Erase 델타:', data.eraseResponses.length);
            
            data.eraseResponses.forEach((erase) => {
              eraseVoxelById(erase.opId);
            });
            
            console.log('[useChunkLoader] ✅ Erase 델타 적용:', data.eraseResponses.length);
          }
          
        } else {
          console.log('[useChunkLoader] ❌ 데이터 없음:', chunkKey);
        }
      }
    };
    
    // 카메라 이동 감지 (debounce 500ms)
    let timeout: number;
    const onCameraMove = () => {
      clearTimeout(timeout);
      timeout = window.setTimeout(loadChunks, 500);
    };
    
    viewer.camera.changed.addEventListener(onCameraMove);
    loadChunks(); // 초기 로드
    
    return () => {
      mounted = false;
      clearTimeout(timeout);
      viewer.camera.changed.removeEventListener(onCameraMove);
    };
  }, [viewer, getTileBaseHeight, enabled, pushVoxelsBatch, eraseVoxelById]);
  
  return { cameraChunkInfo, currentLOD: currentLODRef.current };
}
