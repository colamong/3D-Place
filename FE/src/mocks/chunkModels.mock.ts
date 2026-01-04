import type { ChunkModelSpec } from '@/features/voxels/types';

export const mockChunkModelSpecs: ChunkModelSpec[] = [
  {
    id: 'mock-chunk-436-198-0',
    url: '/glb/glbWithMetadata.glb',
    tile: { x: 436, y: 198 },
    chunk: { cx: 0, cy: 0, ck: 0 },
    zoom: 9,
    origin: 'corner',
    unitScale: 1,
    allowPicking: false,
    debug: false,
    localOffset: { z: -0.5 }, 
  },
];
