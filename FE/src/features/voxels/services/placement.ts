//FE\src\features\voxels\services\placement.ts
import { Cartesian2, Cartesian3, Matrix4, Transforms } from 'cesium';
import type { Viewer as CesiumViewer } from 'cesium';

import { pickFaceByRayAABB, chooseDirByLocal } from '@/features/voxels/pick';
import { offsetByFaceOnGlobalGrid } from '@/features/voxels/geo';
import { getMockVoxelById, type MockVoxelGrid } from '@/features/voxels/mockVoxels.registry';
import { centerFromIndex, keysFromCenter } from '@/features/voxels/chunk';
import type { Voxel, DraftVoxel } from '@/features/voxels/types';
import type { Face } from '@/features/voxels/geo';

type Deps = {
  getViewer: () => CesiumViewer | undefined;                  // () => Cesium.Viewer | undefined
  pickHit: (pos2: Cartesian2) => Cartesian3 | null;      // 깊이 기반 or globe 픽
  getTileBaseHeight: (z: number, x: number, y: number) => number;
  pushDraft: (d: Omit<DraftVoxel, 'id'>) => void;
  ZOOM: number;
  VOXEL_SIZE_M: number;
  color: { r: number; g: number; b: number };
};

type BaseAnchor = {
  center: Cartesian3;
  grid?: MockVoxelGrid;
};

const cloneTile = (tile: { x: number; y: number }) => ({ x: tile.x, y: tile.y });

function ensureGridMeta(
  center: Cartesian3,
  provided: MockVoxelGrid | undefined,
  zoom: number,
  getTileBaseHeight: (z: number, x: number, y: number) => number,
): MockVoxelGrid {
  if (provided) return { ...provided, tile: cloneTile(provided.tile) };
  const keys = keysFromCenter(center, getTileBaseHeight, zoom);
  return {
    z: keys.z,
    tile: { x: keys.tx, y: keys.ty },
    ix: keys.ix,
    iy: keys.iy,
    k: keys.k,
  };
}

function offsetByGrid(
  anchor: MockVoxelGrid,
  face: Face,
  getTileBaseHeight: (z: number, x: number, y: number) => number,
) {
  let ix = anchor.ix;
  let iy = anchor.iy;
  let k = anchor.k;
  if (face === 'E') ix += 1;
  else if (face === 'W') ix -= 1;
  else if (face === 'N') iy += 1;
  else if (face === 'S') iy -= 1;
  else if (face === 'U') k += 1;
  else if (face === 'D') k -= 1;

  const center = centerFromIndex(
    {
      tile: anchor.tile,
      ix,
      iy,
      k,
      zoom: anchor.z,
    },
    getTileBaseHeight,
  );
  const keys = keysFromCenter(center, getTileBaseHeight, anchor.z);
  const grid: MockVoxelGrid = {
    z: keys.z,
    tile: { x: keys.tx, y: keys.ty },
    ix: keys.ix,
    iy: keys.iy,
    k: keys.k,
  };
  return { center, tx: keys.tx, ty: keys.ty, k: keys.k, grid };
}

/** 보셀 엔티티 면을 클릭한 위치에 인접 드래프트 1칸 생성 */
export function makeDraftOnFace(
  pos2: Cartesian2,
  pickVoxelEntity: (pos: Cartesian2) => unknown | null,
  voxels: Record<string, Voxel>,
  { getViewer, pickHit, getTileBaseHeight, pushDraft, ZOOM, VOXEL_SIZE_M, color }: Deps,
): boolean {
  const ent = pickVoxelEntity(pos2);
  if (!ent) return false;

  const voxelId: string | undefined = (() => {
    if (typeof ent === 'string') return ent;
    const id = (ent as { id?: unknown })?.id;
    return typeof id === 'string' ? id : undefined;
  })();
  const baseAnchor: BaseAnchor | undefined = (() => {
    if (!voxelId) return undefined;
    const v = voxels[voxelId];
    if (v) return { center: v.center };
    const mock = getMockVoxelById(voxelId);
    if (mock) return { center: mock.center, grid: mock.grid };
    return undefined;
  })();
  if (!baseAnchor) return false;
  const baseCenter = baseAnchor.center;
  
  const v = getViewer();
  if (!v) return false;

  // 1) 면 노멀 구하기
  let dir = pickFaceByRayAABB(v, pos2, baseCenter, VOXEL_SIZE_M);
  if (!dir) {
    const hit = pickHit(pos2);
    if (!hit) return false;
    const enu = Transforms.eastNorthUpToFixedFrame(baseCenter);
    const inv = Matrix4.inverse(enu, new Matrix4());
    const local = Matrix4.multiplyByPoint(inv, hit, new Cartesian3());
    // 1차: 로컬 좌표 기반 분류
    dir = chooseDirByLocal(local, VOXEL_SIZE_M/2, VOXEL_SIZE_M/2, VOXEL_SIZE_M/2);
  }
  if (!dir) return false; 
  
  // 2차: 엣지 편향(상/하면으로 잡혀도 엣지면 측면 우선)
  const EDGE_TOL = VOXEL_SIZE_M * 0.2;
  try {
    const hit = pickHit(pos2);
    if (hit && (dir === 'U' || dir === 'D')) {
      const enu = Transforms.eastNorthUpToFixedFrame(baseCenter);
      const inv = Matrix4.inverse(enu, new Matrix4());
      const local = Matrix4.multiplyByPoint(inv, hit, new Cartesian3());
      const hx = VOXEL_SIZE_M/2, hy = VOXEL_SIZE_M/2;
      const dx = Math.abs(Math.abs(local.x) - hx);
      const dy = Math.abs(Math.abs(local.y) - hy);
      if (dx < EDGE_TOL && dx <= dy) dir = local.x >= 0 ? 'E' : 'W';
      else if (dy < EDGE_TOL && dy < dx) dir = local.y >= 0 ? 'N' : 'S';
    }
  } catch { /* noop */ }
  if (!dir) return false;

  // 2) 그 방향으로 1m 격자 이동 → 타일/층산출
  let nextCenter: Cartesian3;
  let tx: number;
  let ty: number;
  let k: number;
  try {
    const baseGrid = ensureGridMeta(
      baseCenter,
      baseAnchor.grid,
      baseAnchor.grid?.z ?? ZOOM,
      getTileBaseHeight,
    );
    const next = offsetByGrid(baseGrid, dir as Face, getTileBaseHeight);
    nextCenter = next.center;
    tx = next.tx;
    ty = next.ty;
    k = next.k;
  } catch {
    const fall = offsetByFaceOnGlobalGrid(
      baseCenter, dir as Face, VOXEL_SIZE_M, ZOOM, getTileBaseHeight,
    );
    nextCenter = fall.center;
    tx = fall.tx;
    ty = fall.ty;
    k = fall.k;
  }
  // 3) 드래프트 추가
  const { r, g, b } = color;
  pushDraft({ z: ZOOM, x: tx, y: ty, k, center: nextCenter, r, g, b });
  return true;
}

/** 이미 화면에 떠있는 고스트 드래프트 옆에 1칸 추가 */
export function makeDraftNextToDraft(
  baseD: DraftVoxel,
  pos2: Cartesian2,
  { getViewer, pickHit, getTileBaseHeight, pushDraft, ZOOM, VOXEL_SIZE_M, color }: Deps,
): boolean {
  const v = getViewer();
  if (!v) return false;

  let dir = pickFaceByRayAABB(v, pos2, baseD.center, VOXEL_SIZE_M);
  if (!dir) {
    const hit = pickHit(pos2);
    if (!hit) return false;
    const enu = Transforms.eastNorthUpToFixedFrame(baseD.center);
    const inv = Matrix4.inverse(enu, new Matrix4());
    const local = Matrix4.multiplyByPoint(inv, hit, new Cartesian3());
    dir = chooseDirByLocal(local, VOXEL_SIZE_M / 2, VOXEL_SIZE_M / 2, VOXEL_SIZE_M / 2);
  }
  if (!dir) return false;
  const { center: nextCenter, tx, ty, k } = offsetByFaceOnGlobalGrid(
    baseD.center, dir as Face, VOXEL_SIZE_M, ZOOM, getTileBaseHeight,
  );

  const { r, g, b } = color;
  pushDraft({ z: ZOOM, x: tx, y: ty, k, center: nextCenter, r, g, b });
  return true;
}
