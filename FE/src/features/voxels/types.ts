// src/features/voxels/types.ts
import type { Cartesian3 } from "cesium";
import type { CesiumComponentRef } from 'resium'
import type { Viewer } from 'cesium';

export type ViewerRef = React.RefObject<CesiumComponentRef<Viewer> | null>;

// src/features/voxels/types.ts
export type Voxel = {
  id: string;
  z: number;
  
  // tile 좌표 추가
  tx: number;
  ty: number;
  // chunk 좌표 추가
  cx: number;
  cy: number;
  cz: number;
  // voxel 좌표 추가
  vx: number;
  vy: number;
  vz: number;
  
  x?: number;  // deprecated: use tx
  y?: number;  // deprecated: use ty
  k?: number;  // deprecated: use cz
  
  center: Cartesian3;
  r: number; 
  g: number; 
  b: number;
  opId?: string;
  vSeq?: number;
};

export type HoverFace = {
  center: Cartesian3;
  normal: 'E' | 'W' | 'N' | 'S' | 'U' | 'D';
  dimX: number;
  dimY: number;
} | null;

// 로컬 저장용 DTO (재구성 쉬운 값만)
export type VoxelDTO = {
  id: string; z: number; x: number; y: number; k: number;
  lon: number; lat: number; height: number;
  r: number; g: number; b: number;
};

export type DraftVoxel = {
  id: string;
  z: number; x: number; y: number; k: number;
  center: Cartesian3;
  r: number; g: number; b: number;
};

export type ChunkModelSpec = {
  id: string;
  url: string;
  tile: { x: number; y: number };
  chunk: { cx: number; cy: number; ck: number };
  zoom?: number;
  origin?: 'cell' | 'corner';
  localOffset?: { x?: number; y?: number; z?: number };
  unitScale?: number;
  allowPicking?: boolean;
  debug?: boolean;
};

/** 타일 ENU 1m 그리드 인덱스 */
export type GridIndex = {
  /** 줌(레벨) */
  z: number;
  /** 타일 좌표 (x,y) */
  tx: number;
  ty: number;
  /** 타일 ENU 기준 1m 격자의 정수 좌표 */
  ix: number;
  iy: number;
  /** 층(고도 인덱스) */
  k: number;
};

/** 청크/로컬까지 포함한 인덱스 (편의용) */
export type ChunkIndex = GridIndex & {
  /** 청크 인덱스 (한 변 CHUNK_N 보셀) */
  cx: number;
  cy: number;
  ck: number;
  /** 청크 내부 로컬 인덱스 [0..CHUNK_N-1] */
  lx: number;
  ly: number;
  lk: number;

  /** 문자열 키들은 있어도 되고 없어도 되는 부가 정보 */
  tileKey?: string;   // `${z}/${tx}/${ty}`
  gridKey?: string;   // `${tileKey}/${ix}/${iy}/${k}`
  chunkKey?: string;  // `${tileKey}/${cx}/${cy}/${ck}`
  voxelKey?: string;  // `${chunkKey}/${lx}/${ly}/${lk}`
};

/** 타일의 기준 고도(베이스 높이) 제공 함수 시그니처 */
export type GetTileBaseHeight = (z: number, tx: number, ty: number) => number;
