import { Cartesian2, Cartesian3, Matrix4, Transforms } from 'cesium';
import type { Viewer as CesiumViewer, Entity as CesiumEntity } from 'cesium';
import { pickFaceByRayAABB, chooseDirByLocal } from '@/features/voxels/pick';
import { offsetByFaceOnGlobalGrid } from '@/features/voxels/geo';
import type { Voxel, DraftVoxel } from '@/features/voxels/types';
import type { Face } from '@/features/voxels/geo';

type Deps = {
  getViewer: () => CesiumViewer | undefined;
  pickHit: (pos2: Cartesian2) => Cartesian3 | null;
  getTileBaseHeight: (z: number, x: number, y: number) => number;
  pushDraft: (d: Omit<DraftVoxel, 'id'>) => void;
  ZOOM: number;
  VOXEL_SIZE_M: number;
  color: { r: number; g: number; b: number };
};

/**
 * 🔹 보셀 또는 GLB Mock 엔티티 클릭 시
 *     인접한 위치에 드래프트 1칸 생성
 */
export function makeDraftOnFace(
  pos2: Cartesian2,
  pickVoxelEntity: (pos: Cartesian2) => CesiumEntity | null,
  voxels: Record<string, Voxel>,
  { getViewer, pickHit, getTileBaseHeight, pushDraft, ZOOM, VOXEL_SIZE_M, color }: Deps
): boolean {
  const ent = pickVoxelEntity(pos2);
  if (!ent) return false;

  const voxelId: string | undefined = (() => {
    if (typeof ent === 'string') return ent;
    const id = (ent as { id?: unknown })?.id;
    return typeof id === 'string' ? id : undefined;
  })();

  const baseV: Voxel | undefined = voxelId ? voxels[voxelId] : undefined;
  const baseCenter: Cartesian3 | undefined =
    baseV?.center ??
    (ent as any)?._mockCenter ??
    (ent as any)?.properties?.getValue?.()?.center;

  if (!baseCenter) return false;

  const viewer = getViewer();
  if (!viewer) return false;

  // 1️⃣ 클릭된 면 방향 계산
  let dir = pickFaceByRayAABB(viewer, pos2, baseCenter, VOXEL_SIZE_M);
  if (!dir) {
    const hit = pickHit(pos2);
    if (!hit) return false;
    const enu = Transforms.eastNorthUpToFixedFrame(baseCenter);
    const inv = Matrix4.inverse(enu, new Matrix4());
    const local = Matrix4.multiplyByPoint(inv, hit, new Cartesian3());
    dir = chooseDirByLocal(local, VOXEL_SIZE_M / 2, VOXEL_SIZE_M / 2, VOXEL_SIZE_M / 2);
  }
  if (!dir) return false;

  // 2️⃣ 엣지 근처일 때 측면 우선 보정
  const EDGE_TOL = VOXEL_SIZE_M * 0.2;
  try {
    const hit = pickHit(pos2);
    if (hit && (dir === 'U' || dir === 'D')) {
      const enu = Transforms.eastNorthUpToFixedFrame(baseCenter);
      const inv = Matrix4.inverse(enu, new Matrix4());
      const local = Matrix4.multiplyByPoint(inv, hit, new Cartesian3());
      const hx = VOXEL_SIZE_M / 2, hy = VOXEL_SIZE_M / 2;
      const dx = Math.abs(Math.abs(local.x) - hx);
      const dy = Math.abs(Math.abs(local.y) - hy);
      if (dx < EDGE_TOL && dx <= dy) dir = local.x >= 0 ? 'E' : 'W';
      else if (dy < EDGE_TOL && dy < dx) dir = local.y >= 0 ? 'N' : 'S';
    }
  } catch { /* noop */ }

  // 3️⃣ 옆 칸 좌표 계산
  const { center: nextCenter, tx, ty, k } = offsetByFaceOnGlobalGrid(
    baseCenter, dir as Face, VOXEL_SIZE_M, ZOOM, getTileBaseHeight
  );

  // 4️⃣ 드래프트 생성
  const { r, g, b } = color;
  pushDraft({ z: ZOOM, x: tx, y: ty, k, center: nextCenter, r, g, b });

  return true;
}

/** 이미 떠 있는 드래프트 옆에 추가 */
export function makeDraftNextToDraft(
  baseD: DraftVoxel,
  pos2: Cartesian2,
  { getViewer, pickHit, getTileBaseHeight, pushDraft, ZOOM, VOXEL_SIZE_M, color }: Deps
): boolean {
  const viewer = getViewer();
  if (!viewer) return false;

  let dir = pickFaceByRayAABB(viewer, pos2, baseD.center, VOXEL_SIZE_M);
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
    baseD.center, dir as Face, VOXEL_SIZE_M, ZOOM, getTileBaseHeight
  );

  const { r, g, b } = color;
  pushDraft({ z: ZOOM, x: tx, y: ty, k, center: nextCenter, r, g, b });
  return true;
}
