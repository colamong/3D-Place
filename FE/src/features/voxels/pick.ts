// src/features/voxels/pick.ts
import { Cartesian2, Cartesian3, Matrix4, Transforms } from "cesium";
import type { Viewer as CesiumViewer } from "cesium";
import type { Face } from "./geo";

export function chooseDirByLocal(local: Cartesian3, hx: number, hy: number, hz: number) {
  const tx = Math.abs(local.x) / hx;
  const ty = Math.abs(local.y) / hy;
  const tz = Math.abs(local.z) / hz;
  if (local.z >= 0 && tz >= 0.30) return 'U' as const;
  if (local.z <  0 && tz >= 0.30) return 'D' as const;
  if (tx >= ty && tx >= tz) return local.x >= 0 ? 'E' : 'W';
  if (ty >= tx && ty >= tz) return local.y >= 0 ? 'N' : 'S';
  return local.z >= 0 ? 'U' : 'D';
}

export function pickFaceByRayAABB(
  viewer: CesiumViewer,
  pos2: Cartesian2,
  center: Cartesian3,
  side: number
): ('E'|'W'|'N'|'S'|'U'|'D') | null {
  const ray = viewer.camera.getPickRay(pos2);
  if (!ray) return null;

  const enu = Transforms.eastNorthUpToFixedFrame(center);
  const inv = Matrix4.inverse(enu, new Matrix4());

  const ro = Matrix4.multiplyByPoint(inv, ray.origin, new Cartesian3());
  const rd = Matrix4.multiplyByPointAsVector(inv, ray.direction, new Cartesian3());

  const hx = side/2, hy = side/2, hz = side/2;
  const safeInv = (d: number) => 1 / (Math.abs(d) < 1e-12 ? (d < 0 ? -1e-12 : 1e-12) : d);

  const invx = safeInv(rd.x), invy = safeInv(rd.y), invz = safeInv(rd.z);
  const tx1 = (-hx - ro.x) * invx, tx2 = (hx - ro.x) * invx;
  const ty1 = (-hy - ro.y) * invy, ty2 = (hy - ro.y) * invy;
  const tz1 = (-hz - ro.z) * invz, tz2 = (hz - ro.z) * invz;

  const tminx = Math.min(tx1, tx2), tmaxx = Math.max(tx1, tx2);
  const tminy = Math.min(ty1, ty2), tmaxy = Math.max(ty1, ty2);
  const tminz = Math.min(tz1, tz2), tmaxz = Math.max(tz1, tz2);

  const tEnter = Math.max(tminx, tminy, tminz);
  const tExit  = Math.min(tmaxx, tmaxy, tmaxz);
  if (tEnter > tExit) return null;

  const EPS = Math.max(1e-5, Math.abs(tEnter) * 1e-4);
  if (Math.abs(tEnter - tminx) <= EPS) return rd.x > 0 ? 'W' : 'E';
  if (Math.abs(tEnter - tminy) <= EPS) return rd.y > 0 ? 'S' : 'N';
  return rd.z > 0 ? 'D' : 'U';
}

export function inferFaceECEF(base: Cartesian3, hit: Cartesian3): Face {
  const v = Cartesian3.normalize(Cartesian3.subtract(hit, base, new Cartesian3()), new Cartesian3());
  const dots = [
    { face: "PX" as Face, d:  v.x },
    { face: "NX" as Face, d: -v.x },
    { face: "PY" as Face, d:  v.y },
    { face: "NY" as Face, d: -v.y },
    { face: "PZ" as Face, d:  v.z },
    { face: "NZ" as Face, d: -v.z },
  ];
  dots.sort((a, b) => b.d - a.d);
  return dots[0].face;
}