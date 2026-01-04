// src/api/world.ts
import axios from 'axios';

export type ChunkData = {
  snapshotUrl: string | null;
  glbUrl: string | null;
  paintResponses: PaintResponse[];
  eraseResponses: EraseResponse[];
};

export type PaintResponse = {
  opId: string;
  vSeq: number;
  voxelIndex: { vix: number; viy: number; viz: number };
  chunkIndex: { worldName: string; tx: number; ty: number; cix: number; ciy: number; ciz: number };
  faceMask: number;
  colorSchema: string;
  colorBytes: string;
  timestamp: string;
};

export type EraseResponse = {
  opId: string;
  vSeq: number;
  timestamp: string;
};

// 월드 API 베이스 URL
const BASE_URL = import.meta.env.VITE_WORLD_API_URL || '/api/world';

/**
 * 청크 데이터 조회
 * 
 * @param world 월드 이름 (예: 'world')
 * @param lod Level of Detail (0~3)
 * @param chunkId 청크 ID (예: 'tx0:ty1:x2:y-1:z3')
 */
export async function fetchChunkData(
  world: string,
  lod: number,
  chunkId: string
): Promise<ChunkData | null> {
  try {
    const url = `${BASE_URL}/${world}/${lod}/${chunkId}`;
    console.log('[fetchChunkData] 요청:', url);
    
    const { data } = await axios.get(url, {
      headers: {
        'Content-Type': 'application/json',
      }
    });
    
    console.log('[fetchChunkData] 응답:', data);
    
    if (data.glbUrl !== undefined || data.snapshotUrl !== undefined) {
      return data;
    }
    
    if (data.data && (data.data.glbUrl !== undefined || data.data.snapshotUrl !== undefined)) {
      return data.data;
    }
    
    return null;
    
  } catch (error) {
    if (axios.isAxiosError(error)) {
      console.warn(`[fetchChunkData] 실패 (${world}/${lod}/${chunkId}):`, {
        status: error.response?.status,
        message: error.response?.data?.message,
      });
    } else {
      console.error('[fetchChunkData] 에러:', error);
    }
    return null;
  }
}