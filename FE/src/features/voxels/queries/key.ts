// React Query 캐시 키
export const paintKeys = {
  all: ['paint'] as const,
  chunk: (chunkId: string) => [...paintKeys.all, 'chunk', chunkId] as const,
  voxel: (voxelId: string) => [...paintKeys.all, 'voxel', voxelId] as const,
};
