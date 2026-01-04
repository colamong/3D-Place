export type DeltaPayload = {
  opId: string;
  vSeq: number;
  coordinate: { x: number; y: number; z: number };
  faceMask: number;
  colorSchema: string;
  colorBytes: string;
  timestamp: string;
  policyTags: string;
};

export type VoxelIndexDTO = {
  ix: number;
  iy: number;
  k: number;
};

export type ChunkIndexDTO = {
  worldName: string;
  tx: number;
  ty: number;
  x: number;
  y: number;
  z: number;
};

export type PaintResponse = {
  opId: string;
  voxelIndex: VoxelIndexDTO;
  chunkIndex: ChunkIndexDTO;
  faceMask: number;
  vSeq: number;
  colorSchema: string;
  colorBytes: string;
  actor: string;
  timestamp: string;
  operationType: 'UPSERT' | 'ERASE';
};
