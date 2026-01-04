// src/features/voxels/queries/toServerPayload.ts
// Region-indexed payload builders (no ECEF in payload)
import { Cartesian3, Ellipsoid, Math as CMath } from 'cesium';
import { indexFromCenter, keysFromCenter } from '@/features/voxels/chunk';
import { regionIdFromLonLat, REGION_ZOOM } from '@/features/voxels/geo';
import type { Voxel } from '@/features/voxels/types';

type GetTileBaseHeight = (z:number,x:number,y:number)=>number;

export type UpsertOp = {
  op: 'upsert';
  opId: string;
  existingOpId: string | null;
  vSeq: number;
  regionId: string; // e.g., "9/303/129"
  ix: number; iy: number; k: number;
  colorSchema: 'RGB1';
  colorBytes: string; // '#RRGGBB'
};

export type EraseOp = {
  op: 'erase';
  opId: string;
  existingOpId: string | null;
  vSeq: number;
  regionId: string;
  ix: number; iy: number; k: number;
};

function uuidLike() {
  const s = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).slice(1);
  return `${s()}${s()}-${s()}-${s()}-${s()}-${s()}${s()}${s()}`;
}

function centerToRegionIndex(center: Cartesian3, getTileBaseHeight: GetTileBaseHeight) {
  const carto = Ellipsoid.WGS84.cartesianToCartographic(center);
  const lon = CMath.toDegrees(carto.longitude);
  const lat = CMath.toDegrees(carto.latitude);
  const rid = regionIdFromLonLat(lon, lat, REGION_ZOOM).id;
  const g = indexFromCenter(center, getTileBaseHeight, REGION_ZOOM);
  return { regionId: rid, ix: g.ix, iy: g.iy, k: g.k };
}

export function buildRegionUpsertOps(
  voxels: { center: Cartesian3; hex: string }[],
  getTileBaseHeight: GetTileBaseHeight,
  startVSeq = 1,
): UpsertOp[] {
  return voxels.map((v, i) => {
    const idx = centerToRegionIndex(v.center, getTileBaseHeight);
    return {
      op: 'upsert',
      opId: uuidLike(),
      existingOpId: null,
      vSeq: startVSeq + i,
      regionId: idx.regionId,
      ix: idx.ix, iy: idx.iy, k: idx.k,
      colorSchema: 'RGB1',
      colorBytes: v.hex.startsWith('#') ? v.hex : `#${v.hex}`,
    } as UpsertOp;
  });
}

export function buildRegionEraseOps(
  centers: ({ center: Cartesian3; id?: string; opId?: string; existingOpId?: string | null; vSeq?: number }[] | Voxel[]),
  getTileBaseHeight: GetTileBaseHeight,
  startVSeq = 1,
): EraseOp[] {
  return centers.map((c, i) => {
    const anyc = c as any;
    const center = anyc.center as Cartesian3;
    const providedId: string | undefined = typeof anyc.id === 'string' ? anyc.id : undefined;
    const opId: string = (typeof anyc.opId === 'string' ? anyc.opId : uuidLike());
    const existing: string | null = (anyc.existingOpId ?? providedId ?? null) as string | null;
    const vSeq = typeof anyc.vSeq === 'number' ? anyc.vSeq : (startVSeq + i);
    const idx = centerToRegionIndex(center, getTileBaseHeight);
    return {
      op: 'erase',
      opId,
      existingOpId: existing,
      vSeq,
      regionId: idx.regionId,
      ix: idx.ix, iy: idx.iy, k: idx.k,
    } as EraseOp;
  });
}

// ===== ECEF ops with Base64 color (server variant) =====
export type EcefPaintOp = {
  opId: string;
  existingOpId: string | null;
  vSeq: number;
  coordinate: { x: number; y: number; z: number };
  faceMask: number;
  colorSchema: 'RGB1';
  colorBytes: string; // Base64 of [r,g,b]
  timestamp: string;
  policyTags: string | null;
};

export function rgbToBase64(r: number, g: number, b: number) {
  try {
    if (typeof window !== 'undefined' && typeof window.btoa === 'function') {
      return window.btoa(String.fromCharCode(r, g, b));
    }
  } catch {}
  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const Buf: any = (globalThis as any).Buffer;
    if (Buf) return Buf.from([r, g, b]).toString('base64');
  } catch {}
  const bin = String.fromCharCode(r, g, b);
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
  const c1 = bin.charCodeAt(0), c2 = bin.charCodeAt(1), c3 = bin.charCodeAt(2);
  const enc1 = c1 >> 2;
  const enc2 = ((c1 & 3) << 4) | (c2 >> 4);
  const enc3 = ((c2 & 15) << 2) | (c3 >> 6);
  const enc4 = c3 & 63;
  return chars.charAt(enc1) + chars.charAt(enc2) + chars.charAt(enc3) + chars.charAt(enc4);
}

export function buildEcefPaintOps(
  items: { center: Cartesian3; r: number; g: number; b: number; faceMask?: number; opId?: string; existingOpId?: string | null; vSeq?: number; timestamp?: string; policyTags?: string | null }[],
  startVSeq = 1,
): EcefPaintOp[] {
  return items.map((it, i) => ({
    opId: it.opId ?? uuidLike(),
    existingOpId: it.existingOpId ?? null,
    vSeq: it.vSeq ?? (startVSeq + i),
    coordinate: {
      x: Math.round(it.center.x),
      y: Math.round(it.center.y),
      z: Math.round(it.center.z),
    },
    faceMask: it.faceMask ?? 63,
    colorSchema: 'RGB1',
    colorBytes: rgbToBase64(it.r, it.g, it.b),
    timestamp: it.timestamp ?? new Date().toISOString(),
    policyTags: it.policyTags ?? null,
  }));
}

export type IndexedPaintOp = {
  opId: string;
  vSeq: number;
  tile: { tx: number; ty: number };
  chunk: { cix: number; ciy: number; ciz: number };
  voxel: { vix: number; viy: number; viz: number };
  colorSchema: 'RGB1';
  colorBytes: string;
  faceMask: number;
  timestamp: string;
  policyTags: string | null;
};

type IndexedPaintInput = {
  center: Cartesian3;
  r: number;
  g: number;
  b: number;
  faceMask?: number;
  opId?: string;
  existingOpId?: string | null;
  vSeq?: number;
  timestamp?: string;
  policyTags?: string | null;
};

export function buildIndexedPaintOps(
  items: IndexedPaintInput[],
  getTileBaseHeight: GetTileBaseHeight,
  startVSeq = 1,
): IndexedPaintOp[] {
  return items.map((it, i) => {
    const idx = keysFromCenter(it.center, getTileBaseHeight);
    return {
      opId: it.opId ?? uuidLike(),
      vSeq: it.vSeq ?? startVSeq + i,
      tile: { tx: idx.tx, ty: idx.ty },
      chunk: { cix: idx.cx, ciy: idx.cy, ciz: idx.ck },
      voxel: { vix: idx.lx, viy: idx.ly, viz: idx.lk },
      colorSchema: 'RGB1',
      colorBytes: rgbToBase64(it.r, it.g, it.b),
      faceMask: it.faceMask ?? 63,
      timestamp: it.timestamp ?? new Date().toISOString(),
      policyTags: it.policyTags ?? null,
    };
  });
}
