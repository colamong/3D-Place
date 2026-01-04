// src/features/voxels/persist.local.ts
import { Cartesian3, Ellipsoid, Math as CMath } from "cesium";
import type { Voxel, VoxelDTO } from "./types";

const KEY = "voxels/v1";

export function persistVoxels(voxels: Record<string, Voxel>) {
  const arr: VoxelDTO[] = Object.values(voxels).map(v => {
    const carto = Ellipsoid.WGS84.cartesianToCartographic(v.center);
    return {
      id: v.id, z: v.z,
      x: (v as any).x ?? 0,
      y: (v as any).y ?? 0,
      k: (v as any).k ?? 0,
      lon: CMath.toDegrees(carto.longitude),
      lat: CMath.toDegrees(carto.latitude),
      height: carto.height,
      r: v.r, g: v.g, b: v.b,
    };
  });
  localStorage.setItem(KEY, JSON.stringify(arr));
}

export function restoreVoxels(): Record<string, Voxel> {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return {};
    const arr = JSON.parse(raw) as VoxelDTO[];
    const out: Record<string, Voxel> = {};
    for (const d of arr) {
      const center = Cartesian3.fromDegrees(d.lon, d.lat, d.height);
      out[d.id] = {
        // minimal voxel compatible shape (indices unknown → default 0)
        id: d.id,
        z: d.z,
        tx: 0, ty: 0, cx: 0, cy: 0, cz: d.k ?? 0,
        vx: 0, vy: 0, vz: 0,
        x: d.x, y: d.y, k: d.k,
        center,
        r: d.r, g: d.g, b: d.b,
      } as Voxel;
    }
    return out;
  } catch {
    return {};
  }
}

export function clearVoxels() {
  localStorage.removeItem(KEY);
}
