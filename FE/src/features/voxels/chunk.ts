import {
  Cartesian3,
  Ellipsoid,
  Math as CMath,
  Matrix4,
  Transforms,
} from 'cesium';
import {
  lonLatToTile,
  enuOfRegionByLonLat,
  tileBounds,
} from './geo';
import {
  ZOOM,
  VOXEL_SIZE_M,
  CHUNK_N,
  CENTER_OFFSET_M,
} from './constants';

export type GetTileBaseHeight = (z: number, x: number, y: number) => number;

export type GridIndex = {
  z: number;
  tx: number;
  ty: number;
  ix: number;
  iy: number;
  k: number;
};

export type ChunkIndex = GridIndex & {
  cx: number;
  cy: number;
  ck: number;
  lx: number;
  ly: number;
  lk: number;
  tileKey: string;
  gridKey: string;
  chunkKey: string;
  voxelKey: string;
};

// (x mod m)를 음수 없이
const mod = (n: number, m: number) => ((n % m) + m) % m;

/** 보셀 센터(ECEF) → 타일 ENU 1m-grid 인덱스(ix,iy,k) */
export function indexFromCenter(
  center: Cartesian3,
  getTileBaseHeight: GetTileBaseHeight,
  zoom: number = ZOOM,
): GridIndex {
  const step = VOXEL_SIZE_M;

  const carto = Ellipsoid.WGS84.cartesianToCartographic(center);
  const lon = CMath.toDegrees(carto.longitude);
  const lat = CMath.toDegrees(carto.latitude);

  const { x: tx, y: ty } = lonLatToTile(lon, lat, zoom);

  // 지역 ENU로 변환
  const { inv } = enuOfRegionByLonLat(lon, lat);
  const local = Matrix4.multiplyByPoint(inv, center, new Cartesian3());

  // 1m 격자(센터 정렬)
  const ix = Math.round((local.x - CENTER_OFFSET_M) / step);
  const iy = Math.round((local.y - CENTER_OFFSET_M) / step);

  // 층(k): base + (k+0.5)*step 공식 역산
  const base = getTileBaseHeight(zoom, tx, ty);
  const k = Math.round((carto.height - base) / step - 0.5);

  return { z: zoom, tx, ty, ix, iy, k };
}

/** 센터 → 그리드/청크/로컬/키 모두 반환 */
export function keysFromCenter(
  center: Cartesian3,
  getTileBaseHeight: GetTileBaseHeight,
  zoom: number = ZOOM,
): ChunkIndex {
  const g = indexFromCenter(center, getTileBaseHeight, zoom);

  const cx = Math.floor(g.ix / CHUNK_N);
  const cy = Math.floor(g.iy / CHUNK_N);
  const ck = Math.floor(g.k / CHUNK_N);

  const lx = mod(g.ix, CHUNK_N);
  const ly = mod(g.iy, CHUNK_N);
  const lk = mod(g.k, CHUNK_N);

  const tileKey = `${g.z}/${g.tx}/${g.ty}`;
  const chunkKey = `${tileKey}/${cx}/${cy}/${ck}`;
  const gridKey = `${tileKey}/${g.ix}/${g.iy}/${g.k}`;
  const voxelKey = `${chunkKey}/${lx}/${ly}/${lk}`;

  return { ...g, cx, cy, ck, lx, ly, lk, tileKey, gridKey, chunkKey, voxelKey };
}

/** 보셀 인덱스(ix,iy,k) → ECEF 좌표 */
export function centerFromIndex(
  idx: { tile: { x: number; y: number }; ix: number; iy: number; k: number; zoom?: number },
  getTileBaseHeight: GetTileBaseHeight,
): Cartesian3 {
  const zoom = idx.zoom ?? ZOOM;
  const step = VOXEL_SIZE_M;
  const { x, y } = idx.tile;

  // 타일 중심 경위도에서 지역 ENU 선택
  const { west, south, east, north } = tileBounds(x, y, zoom);
  const midLon = (west + east) / 2;
  const midLat = (south + north) / 2;
  const { enu } = enuOfRegionByLonLat(midLon, midLat);

  // 로컬(ENU) 좌표의 보셀 센터
  const lx = idx.ix * step + CENTER_OFFSET_M;
  const ly = idx.iy * step + CENTER_OFFSET_M;

  // ENU → ECEF 변환
  const p0 = Matrix4.multiplyByPoint(enu, new Cartesian3(lx, ly, 0), new Cartesian3());
  const c = Ellipsoid.WGS84.cartesianToCartographic(p0);
  const lon = CMath.toDegrees(c.longitude);
  const lat = CMath.toDegrees(c.latitude);

  // 높이 계산: “센터 기준” (corner는 나중에 보정)
  const base = getTileBaseHeight(zoom, x, y);
  const h = base + (idx.k + 0.5) * step;

  return Cartesian3.fromDegrees(lon, lat, h);
}

type ChunkCoordInput = {
  tile: { x: number; y: number };
  chunk: { cx: number; cy: number; ck: number };
  zoom?: number;
};

type ChunkLocalOffset = { lx?: number; ly?: number; lk?: number };

/** 청크 인덱스 기반 첫 보셀의 중심 좌표 */
export function centerFromChunkIndices(
  {
    tile,
    chunk,
    local,
    getTileBaseHeight,
    zoom,
  }: ChunkCoordInput & {
    local?: ChunkLocalOffset;
    getTileBaseHeight: GetTileBaseHeight;
    zoom?: number;
  },
): Cartesian3 {
  const { cx, cy, ck } = chunk;
  const { lx = 0, ly = 0, lk = 0 } = local ?? {};
  const ix = cx * CHUNK_N + lx;
  const iy = cy * CHUNK_N + ly;
  const k = ck * CHUNK_N + lk;
  return centerFromIndex({ tile, ix, iy, k, zoom }, getTileBaseHeight);
}

export type ChunkModelMatrixParams = ChunkCoordInput & {
  getTileBaseHeight: GetTileBaseHeight;
  origin?: 'cell' | 'corner';
  unitScale?: number;
  localOffset?: { x?: number; y?: number; z?: number };
  zoom?: number;
};

/** 청크의 모델 행렬(ECEF 기준) 생성 */
export function buildChunkModelMatrix({
  tile,
  chunk,
  getTileBaseHeight,
  origin = 'cell',
  unitScale = VOXEL_SIZE_M,
  localOffset,
  zoom,
}: ChunkModelMatrixParams): Matrix4 {
  // 첫 보셀 중심을 anchor로 잡음
  const firstCellCenter = centerFromChunkIndices({
    tile,
    chunk,
    getTileBaseHeight,
    zoom,
  });

  const enuAtCell = Transforms.eastNorthUpToFixedFrame(firstCellCenter);

  let anchorPoint = firstCellCenter;

  // origin이 corner면, 반 셀(-0.5)만큼 내림
  if (origin === 'corner') {
    anchorPoint = Matrix4.multiplyByPoint(
      enuAtCell,
    new Cartesian3(-VOXEL_SIZE_M / 2, -VOXEL_SIZE_M / 2, -VOXEL_SIZE_M / 2),
      new Cartesian3(),
    );
  }

  const anchorEnu = Transforms.eastNorthUpToFixedFrame(anchorPoint);
  const scaleMatrix = Matrix4.fromUniformScale(unitScale, new Matrix4());

  // Compose local transform as Translation * Scale so translation is not scaled.
  let localMatrix = scaleMatrix;
  if (localOffset) {
    const dx = localOffset.x ?? 0;
    const dy = localOffset.y ?? 0;
    const dz = localOffset.z ?? 0;
    if (dx !== 0 || dy !== 0 || dz !== 0) {
      const translate = Matrix4.fromTranslation(new Cartesian3(dx, dy, dz), new Matrix4());
      // T * S (so translation is in meters, not scaled by unitScale)
      localMatrix = Matrix4.multiply(translate, localMatrix, new Matrix4());
    }
  }
  // Final model matrix: Anchor_ENU * Local
  return Matrix4.multiply(anchorEnu, localMatrix, new Matrix4());
}
