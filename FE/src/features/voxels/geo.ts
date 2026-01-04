import { Cartesian3, Ellipsoid, Math as CMath, Matrix4, Transforms } from "cesium";
import type { Viewer as CesiumViewer } from 'cesium';
import { GAP, CENTER_OFFSET_M } from "./constants";

let GLOBAL_ENU: Matrix4 | null = null;
let GLOBAL_INV: Matrix4 | null = null;
const FIXED_ANCHOR = { LON: 0, LAT: 0, HEIGHT: 0 as const };

export function setGlobalEnuAnchorByLonLat(lonDeg: number, latDeg: number, height = 0) {
  const origin = Cartesian3.fromDegrees(lonDeg, latDeg, height);
  GLOBAL_ENU = Transforms.eastNorthUpToFixedFrame(origin);
  GLOBAL_INV = Matrix4.inverse(GLOBAL_ENU, new Matrix4());
}

function ensureGlobalEnuFor(_world: Cartesian3) {
  if (GLOBAL_ENU && GLOBAL_INV) return;
  void _world;
  setGlobalEnuAnchorByLonLat(FIXED_ANCHOR.LON, FIXED_ANCHOR.LAT, FIXED_ANCHOR.HEIGHT);
}

export function getGlobalEnu() {
  return GLOBAL_ENU && GLOBAL_INV ? { enu: GLOBAL_ENU, inv: GLOBAL_INV } : null;
}

export function ensureGlobalEnuByLonLat(_lonDeg: number, _latDeg: number, _height = 0) {
  if (!GLOBAL_ENU || !GLOBAL_INV) setGlobalEnuAnchorByLonLat(FIXED_ANCHOR.LON, FIXED_ANCHOR.LAT, FIXED_ANCHOR.HEIGHT);
  void _lonDeg; void _latDeg; void _height;
  return { enu: GLOBAL_ENU!, inv: GLOBAL_INV! };
}

export function ensureGlobalEnuFromViewer(_v?: CesiumViewer) {
  if (!GLOBAL_ENU || !GLOBAL_INV) setGlobalEnuAnchorByLonLat(FIXED_ANCHOR.LON, FIXED_ANCHOR.LAT, FIXED_ANCHOR.HEIGHT);
  void _v;
  return { enu: GLOBAL_ENU!, inv: GLOBAL_INV! };
}

export function ensureGlobalEnuFromWorld(world: Cartesian3) {
  ensureGlobalEnuFor(world);
  return { enu: GLOBAL_ENU!, inv: GLOBAL_INV! };
}

export type Face = 'E' | 'W' | 'N' | 'S' | 'U' | 'D';

export function offsetByFaceOnGlobalGrid(
  baseCenter: Cartesian3,
  face: Face,
  step: number,
  z: number,
  getTileBaseHeight: (z: number, x: number, y: number) => number,
) {
  const carto0 = Ellipsoid.WGS84.cartesianToCartographic(baseCenter);
  const lon0 = CMath.toDegrees(carto0.longitude);
  const lat0 = CMath.toDegrees(carto0.latitude);
  const { x: tx0, y: ty0 } = lonLatToTile(lon0, lat0, z);
  const baseH0 = getTileBaseHeight(z, tx0, ty0);
  let k = Math.max(0, Math.round((carto0.height - baseH0) / step - 0.5));

  const { enu: regionEnu, inv: regionInv } = enuOfRegionByLonLat(lon0, lat0);
  const local = Matrix4.multiplyByPoint(regionInv, baseCenter, new Cartesian3());
  
  const baseOffset = CENTER_OFFSET_M;

  const ix = Math.round((local.x - baseOffset) / step);
  const iy = Math.round((local.y - baseOffset) / step);
  let lx = ix * step + baseOffset;
  let ly = iy * step + baseOffset;

  if (face === 'E') lx += step;
  else if (face === 'W') lx -= step;
  else if (face === 'N') ly += step;
  else if (face === 'S') ly -= step;
  else if (face === 'U') k += 1;
  else if (face === 'D') k = Math.max(0, k - 1);

  const xyWorld = Matrix4.multiplyByPoint(regionEnu, new Cartesian3(lx, ly, 0), new Cartesian3());
  const carto = Ellipsoid.WGS84.cartesianToCartographic(xyWorld);
  const lon = CMath.toDegrees(carto.longitude);
  const lat = CMath.toDegrees(carto.latitude);
  const { x: tx, y: ty } = lonLatToTile(lon, lat, z);
  const baseH = getTileBaseHeight(z, tx, ty);
  const h = baseH + (k + 0.5) * step;

  const center = Cartesian3.fromDegrees(lon, lat, h);
  return { center, tx, ty, k };
}

export function lonLatToTile(lon: number, lat: number, z: number) {
  const latRad = (lat * Math.PI) / 180, n = 2 ** z;
  const x = Math.floor(((lon + 180) / 360) * n);
  const y = Math.floor((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n);
  return { x, y };
}

export function tileBounds(x: number, y: number, z: number) {
  const n = 2 ** z;
  const west = (x / n) * 360 - 180;
  const east = ((x + 1) / n) * 360 - 180;
  const north = (Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n))) * 180) / Math.PI;
  const south = (Math.atan(Math.sinh(Math.PI * (1 - 2 * (y + 1) / n))) * 180) / Math.PI;
  return { west, south, east, north };
}

export function enuAtTileCenter(z: number, x: number, y: number) {
  const { west, south, east, north } = tileBounds(x, y, z);
  const lon = (west + east) / 2, lat = (south + north) / 2;
  const origin = Cartesian3.fromDegrees(lon, lat, 0);
  const enu = Transforms.eastNorthUpToFixedFrame(origin);
  const inv = Matrix4.inverse(enu, new Matrix4());
  return { enu, inv };
}



export function mapLocalFaceToGlobal(baseCenter: Cartesian3, localFace: Face): Face {
  const enu = Transforms.eastNorthUpToFixedFrame(baseCenter);
  const nLocal = (() => {
    if (localFace === 'E') return new Cartesian3( 1, 0, 0);
    if (localFace === 'W') return new Cartesian3(-1, 0, 0);
    if (localFace === 'N') return new Cartesian3( 0, 1, 0);
    if (localFace === 'S') return new Cartesian3( 0,-1, 0);
    if (localFace === 'U') return new Cartesian3( 0, 0, 1);
    return new Cartesian3(0, 0, -1);
  })();
  const nECEF = Matrix4.multiplyByPointAsVector(enu, nLocal, new Cartesian3());
  const nG = Matrix4.multiplyByPointAsVector(GLOBAL_INV!, nECEF, new Cartesian3());
  const ax = Math.abs(nG.x), ay = Math.abs(nG.y), az = Math.abs(nG.z);
  if (ax >= ay && ax >= az) return nG.x >= 0 ? 'E' : 'W';
  if (ay >= ax && ay >= az) return nG.y >= 0 ? 'N' : 'S';
  return nG.z >= 0 ? 'U' : 'D';
}

export function tileSizeMeters(x: number, y: number, z: number) {
  const { west, south, east, north } = tileBounds(x, y, z);
  const midLon = (west + east) / 2, midLat = (south + north) / 2;
  const pW = Cartesian3.fromDegrees(west,  midLat);
  const pE = Cartesian3.fromDegrees(east,  midLat);
  const pS = Cartesian3.fromDegrees(midLon, south);
  const pN = Cartesian3.fromDegrees(midLon, north);
  return {
    widthM:  Cartesian3.distance(pW, pE),
    heightM: Cartesian3.distance(pS, pN),
    midLon, midLat
  };
}

export function tileDims(z: number, x: number, y: number) {
  const { widthM, heightM } = tileSizeMeters(x, y, z);
  const dimX = Math.max(1, widthM  - 2 * GAP);
  const dimY = Math.max(1, heightM - 2 * GAP);
  const side = Math.max(1, Math.min(dimX, dimY));
  return { dimX, dimY, side };
}

export function wrapX(x: number, z: number) {
  const n = 2 ** z;
  return (x % n + n) % n;
}
export function clampY(y: number, z: number) {
  const n = 2 ** z;
  return Math.max(0, Math.min(n - 1, y));
}

export function snapECEF(p: Cartesian3, step: number, out = new Cartesian3()) {
  out.x = Math.round(p.x / step) * step;
  out.y = Math.round(p.y / step) * step;
  out.z = Math.round(p.z / step) * step;
  return out;
}

// ----- Region ENU (WebMercator tile at fixed zoom) -----
export const REGION_ZOOM = 9;
const REGION_CACHE = new Map<string, { enu: Matrix4; inv: Matrix4 }>();

export function regionIdFromLonLat(lonDeg: number, latDeg: number, zr = REGION_ZOOM) {
  const { x, y } = lonLatToTile(lonDeg, latDeg, zr);
  return { id: `${zr}/${x}/${y}`, x, y, z: zr };
}

export function enuOfRegionByLonLat(lonDeg: number, latDeg: number, zr = REGION_ZOOM) {
  const { id, x, y } = regionIdFromLonLat(lonDeg, latDeg, zr);
  const cached = REGION_CACHE.get(id);
  if (cached) return cached!;
  const { west, south, east, north } = tileBounds(x, y, zr);
  const lon = (west + east) / 2, lat = (south + north) / 2;
  const origin = Cartesian3.fromDegrees(lon, lat, 0);
  const enu = Transforms.eastNorthUpToFixedFrame(origin);
  const inv = Matrix4.inverse(enu, new Matrix4());
  const pair = { enu, inv };
  REGION_CACHE.set(id, pair);
  return pair;
}