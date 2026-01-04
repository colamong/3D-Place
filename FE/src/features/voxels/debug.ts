// features/voxels/debug.ts
import {
  Cartesian3, Ellipsoid, Math as CMath, Matrix4, Transforms
} from "cesium";
import { lonLatToTile, tileSizeMeters } from "./geo";
import { ZOOM, VOXEL_SIZE_M, CHUNK_N, BASE_LIFT } from "./constants";

/** ---- utils ---- */
type Row = {
  id?: string;
  center: Cartesian3;       // ECEF
  r?: number; g?: number; b?: number;
  hex?: string;             // '#RRGGBB' 우선
  size_m?: number;          // 한 변(미터)
};

const posMod = (n: number, m: number) => ((n % m) + m) % m;
const clampByte = (n: number) => Math.max(0, Math.min(255, Math.round(n)));
const toHex2 = (n: number) => clampByte(n).toString(16).padStart(2, "0").toUpperCase();
const rgbToHex = (r = 255, g = 255, b = 255) => `#${toHex2(r)}${toHex2(b)}${toHex2(b)}`;

/** 타일 중심 고도(기존 getTileBaseHeight와 동일 공식) */
function getTileBaseHeight(z: number, x: number, y: number) {
  const { midLon, midLat } = tileSizeMeters(x, y, z);
  const cart = Ellipsoid.WGS84.cartesianToCartographic(
    Cartesian3.fromDegrees(midLon, midLat),
  );
  return (cart.height || 0) + BASE_LIFT;
}

/** ECEF center -> 타일/격자/청크/셀/키 계산 */
export function chunkInfoFromCenter(center: Cartesian3) {
  const step = VOXEL_SIZE_M;

  const carto = Ellipsoid.WGS84.cartesianToCartographic(center);
  const lon = CMath.toDegrees(carto.longitude);
  const lat = CMath.toDegrees(carto.latitude);
  const { x: tx, y: ty } = lonLatToTile(lon, lat, ZOOM);

  const { midLon, midLat } = tileSizeMeters(tx, ty, ZOOM);
  const origin = Cartesian3.fromDegrees(midLon, midLat, 0);
  const enu = Transforms.eastNorthUpToFixedFrame(origin);
  const inv = Matrix4.inverse(enu, new Matrix4());
  const local = Matrix4.multiplyByPoint(inv, center, new Cartesian3());

  const ix = Math.round(local.x / step);
  const iy = Math.round(local.y / step);

  const base = getTileBaseHeight(ZOOM, tx, ty);
  const k = Math.round((carto.height - base) / step - 0.5);

  const cx = Math.floor(ix / CHUNK_N);
  const cy = Math.floor(iy / CHUNK_N);
  const cz = Math.floor(k  / CHUNK_N);

  const lx = posMod(ix, CHUNK_N);
  const ly = posMod(iy, CHUNK_N);
  const lz = posMod(k,  CHUNK_N);

  const chunkKey = `${ZOOM}/${tx}/${ty}/${cx}/${cy}/${cz}`;
  const cellKey  = `${chunkKey}/${lx}/${ly}/${lz}`;

  return {
    tx, ty, ix, iy, k,
    cx, cy, cz,
    lx, ly, lz,
    chunkKey, cellKey,
    lon, lat, h: carto.height
  };
}

const voxelIdFromLocal = (lx: number, ly: number, lz: number) =>
  (lz * CHUNK_N + ly) * CHUNK_N + lx;

/** 서버 전송 스키마 형태 + ECEF(정수m) 포함해서 로깅 */
export function logVoxelEventsForServer(tag: string, rows: Row[]) {
  if (!rows?.length) { console.warn(`[${tag}] empty`); return []; }

  const events = rows.map((r) => {
    const info = chunkInfoFromCenter(r.center);
    const voxelId = voxelIdFromLocal(info.lx, info.ly, info.lz);
    const hex = r.hex ?? rgbToHex(r.r, r.g, r.b);

    return {
      zoom: ZOOM,
      tile: { x: info.tx, y: info.ty },
      chunkCoordinate: { x: info.cx, y: info.cy, z: info.cz },
      voxelId,                                  // 평탄화 ID (셀이 256^3일 때 0..16,777,215)
      voxelIndex: { x: info.lx, y: info.ly, z: info.lz }, // 필요 시 디버그용
      cellKey: info.cellKey,                    // 전역 유니크 키
      colorSchema: "RGB1",
      colorBytes: hex,
      faceMask: 63,
      ecefCenterM: {                            // 정수(미터) ECEF
        x: Math.round(r.center.x),
        y: Math.round(r.center.y),
        z: Math.round(r.center.z),
      },
      timestamp: new Date().toISOString(),
      policyTags: null,
    };
  });

  console.groupCollapsed(`%c${tag} (events: ${events.length})`, "color:#0bf;font-weight:700;");
  console.table(events.map(e => ({
    zoom: e.zoom,
    tile: `${e.tile.x},${e.tile.y}`,
    chunk: `${e.chunkCoordinate.x},${e.chunkCoordinate.y},${e.chunkCoordinate.z}`,
    voxelId: e.voxelId,
    voxelIndex: `${e.voxelIndex.x},${e.voxelIndex.y},${e.voxelIndex.z}`,
    cellKey: e.cellKey,
    ecefCenterM: `${e.ecefCenterM.x},${e.ecefCenterM.y},${e.ecefCenterM.z}`,
    color: e.colorBytes,
    faceMask: e.faceMask
  })));
  console.groupEnd();

  // 필요하면 실제 전송 페이로드로도 재사용 가능
  console.log(`${tag} payload:`, events);
  return events;
}
