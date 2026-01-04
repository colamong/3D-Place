// src/features/voxels/utils/chunkLoader.ts
import { Viewer, Cartesian2, Cartesian3 } from 'cesium';
import { indexFromCenter } from '@/features/voxels/chunk';
import { CHUNK_N, ZOOM } from '@/features/voxels/constants';
import type { GetTileBaseHeight } from '@/features/voxels/types';

export type ChunkId = {
  world: string;
  lod: number;
  tx: number;
  ty: number;
  cx: number;
  cy: number;
  cz: number;
};

export type CameraChunkInfo = {
  tx: number;
  ty: number;
  cx: number;
  cy: number;
  cz: number;
  ix: number;
  iy: number;
  heightAboveGround: number;
  currentLOD: number;
};

// LOD 임계값 (미터) — /_dev/lod 기준과 동일
// Vite env로 조정 가능 (기본: 6.5km / 15km / 23.095km)
const LOD0_MAX_M = Number((import.meta as any).env?.VITE_LOD0_MAX_M ?? 6500);
const LOD1_MAX_M = Number((import.meta as any).env?.VITE_LOD1_MAX_M ?? 15000);
const LOD2_MAX_M = Number((import.meta as any).env?.VITE_LOD2_MAX_M ?? 23095);

/**
 * 카메라 높이를 기반으로 LOD 결정
 * 
 * - 높이 < 6.5km: LOD 0
 * - 6.5km ~ 15km: LOD 1
 * - 15km ~ 23.095km: LOD 2
 * - 23.095km+: LOD 3
 */
export function calculateLODByHeight(heightAboveGround: number): number {
  const h = heightAboveGround;
  
  if (h < LOD0_MAX_M) return 0;
  if (h < LOD1_MAX_M) return 1;
  if (h < LOD2_MAX_M) return 2;
  return 3;
}

/**
 * 카메라 위치를 청크 인덱스로 변환
 * 
 * ZOOM=9는 항상 고정이고, LOD는 카메라 높이에 따라 동적으로 변경됨
 */
export function getCameraChunkIndices(
  viewer: Viewer,
  getTileBaseHeight: GetTileBaseHeight
): CameraChunkInfo | null {
  const cameraPos = viewer.camera.position;
  
  try {
    // 👈 보셀 좌표는 항상 ZOOM=9로 인덱싱
    const idx = indexFromCenter(cameraPos, getTileBaseHeight, ZOOM);
    
    // ✅ 카메라 고도 (Ellipsoid 기준, 카메라가 제공하는 Cartographic은 이미 라디안/미터)
    const heightAboveGround = viewer.camera.positionCartographic.height;
    
    // ✅ 높이 기반 LOD 계산
    const currentLOD = calculateLODByHeight(heightAboveGround);
    
    const cx = Math.floor(idx.ix / CHUNK_N);
    const cy = Math.floor(idx.iy / CHUNK_N);
    const cz = 0;
    
    console.log('[getCameraChunkIndices]:', {
      heightAboveGround: Math.round(heightAboveGround),
      currentLOD,
    });
    
    return {
      tx: idx.tx,
      ty: idx.ty,
      cx,
      cy,
      cz,
      ix: idx.ix,
      iy: idx.iy,
      heightAboveGround,
      currentLOD,
    };
  } catch (err) {
    console.warn('[getCameraChunkIndices] 카메라 청크 인덱스 계산 실패:', err);
    return null;
  }
}

/**
 * 화면에 보이는 청크들을 현재 LOD로 계산
 * 
 * ZOOM=9는 항상 고정으로 인덱싱하고,
 * LOD는 카메라 높이에 따라 동적으로 결정됨
 */
export function getVisibleChunks(
  viewer: Viewer,
  getTileBaseHeight: GetTileBaseHeight,
  currentLOD: number,
  worldName: string = 'world'
): ChunkId[] {
  const scene = viewer.scene;
  const camera = viewer.camera;
  const canvas = scene.canvas;
  
  // 화면의 4개 모서리 포지션
  const corners = [
    new Cartesian2(0, 0),
    new Cartesian2(canvas.clientWidth, 0),
    new Cartesian2(0, canvas.clientHeight),
    new Cartesian2(canvas.clientWidth, canvas.clientHeight),
  ];
  
  const positions = corners
    .map(c => camera.pickEllipsoid(c, scene.globe.ellipsoid))
    .filter((p): p is Cartesian3 => p !== undefined);
  
  if (positions.length === 0) return [];
  
  const chunkSet = new Set<string>();
  
  for (const pos of positions) {
    try {
      const idx = indexFromCenter(pos, getTileBaseHeight, ZOOM);
      
      const cx = Math.floor(idx.ix / CHUNK_N);
      const cy = Math.floor(idx.iy / CHUNK_N);
      const cz = 0;
      
      const chunkKey = `${worldName}|${currentLOD}|${idx.tx}|${idx.ty}|${cx}|${cy}|${cz}`;
      chunkSet.add(chunkKey);
    } catch (err) {
      console.warn('[getVisibleChunks] 청크 계산 실패:', err);
    }
  }
  
  return Array.from(chunkSet).map(key => {
    const [world, lodStr, txStr, tyStr, cxStr, cyStr, czStr] = key.split('|');
    return {
      world,
      lod: Number(lodStr),
      tx: Number(txStr),
      ty: Number(tyStr),
      cx: Number(cxStr),
      cy: Number(cyStr),
      cz: Number(czStr),
    };
  });
}

export function chunkIdToString(chunk: ChunkId): string {
  return `tx${chunk.tx}:ty${chunk.ty}:x${chunk.cx}:y${chunk.cy}:z${chunk.cz}`;
}
