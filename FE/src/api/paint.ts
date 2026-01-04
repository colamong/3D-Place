// Adapter for legacy imports
import { fetchChunkData } from '@/api/world';
import { postPaints, deletePaints } from '@/api/paints';

export async function getPaintsByChunk(chunkId: string) {
  const world = (import.meta as any)?.env?.VITE_WORLD_NAME || 'exampleWorld';
  const lod = Number((import.meta as any)?.env?.VITE_WORLD_FETCH_LOD ?? (import.meta as any)?.env?.VITE_WORLD_LOD ?? 9);
  const data = await fetchChunkData(world, lod, chunkId);
  return data?.paintResponses ?? [];
}

export async function postPaint(payload: any) {
  const arr = Array.isArray(payload) ? payload : [payload];
  return postPaints(arr as any);
}

export async function postErase(payload: any) {
  const arr = Array.isArray(payload) ? payload : [payload];
  return deletePaints(arr as any);
}

