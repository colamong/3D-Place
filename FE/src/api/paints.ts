// src/api/paints.ts
import { api } from './instance';
import { getMyProfile } from './user';

export type ServerPaintEvent = {
  opId: string;
  existingOpId: string | null;
  vSeq: number;
  voxelIndex: { vix: number; viy: number; viz: number };
  chunkIndex: {
    worldName: string;
    tx: number;
    ty: number;
    cix: number;
    ciy: number;
    ciz: number;
  };
  faceMask?: number;
  colorSchema?: 'RGB1' | 'FACE';
  colorBytes?: string;
  timestamp: string;
  operationType?: 'UPSERT' | 'UPDATE' | 'ERASE' | 'DELETE';
  policyTags?: string | null;
};

/**
 * 보셀 페인트 이벤트를 서버에 전송
 * 
 * @param events 페인트 이벤트 배열
 */
export async function postPaints(events: ServerPaintEvent[]) {
  const payload = events.map((e) => ({
    opId: e.opId,
    existingOpId: e.existingOpId ?? null,
    vSeq: e.vSeq,
    voxelIndex: e.voxelIndex,
    chunkIndex: {
      ...e.chunkIndex,
      worldName: 'world', // ✅ 고정
    },
    faceMask: e.faceMask ?? 63,
    colorSchema: e.colorSchema ?? 'RGB1',
    colorBytes: e.colorBytes,
    timestamp: e.timestamp,
    policyTags: e.policyTags ?? null,
  }));

  console.log(
    '[postPaints] Sending payload:',
    JSON.stringify(payload, null, 2),
  );

  try {
    const headers = await resolvePaintHeaders();
    const { data } = await api.post('/paints/', payload, headers ? { headers } : undefined);
    console.log('[postPaints] Success!', data);
    return data;
  } catch (error) {
    logAxiosError('postPaints', error);
    throw error;
  }
}

/**
 * 보셀 삭제 이벤트를 서버에 전송
 * 
 * @param events 삭제 이벤트 배열
 */
export async function deletePaints(events: ServerPaintEvent[]) {
  const payload = events.map((e) => ({
    opId: e.opId,
    existingOpId: e.existingOpId ?? null,
    vSeq: e.vSeq,
    voxelIndex: e.voxelIndex,
    chunkIndex: {
      ...e.chunkIndex,
      worldName: 'world', // ✅ 고정
    },
    timestamp: e.timestamp,
    policyTags: e.policyTags ?? null,
  }));

  console.log(
    '[deletePaints] Sending payload:',
    JSON.stringify(payload, null, 2),
  );

  try {
    const headers = await resolvePaintHeaders();
    const { data } = await api.delete('/paints/erase', {
      data: payload,
      ...(headers ? { headers } : {}),
    });
    console.log('[deletePaints] Success!', data);
    return data;
  } catch (error) {
    logAxiosError('deletePaints', error);
    throw error;
  }
}

function logAxiosError(label: string, error: unknown) {
  const res = (error as { response?: { status?: number; data?: unknown } })
    ?.response;
  if (res) {
    console.error(`[${label}] Error:`, {
      status: res.status,
      data: res.data,
    });
  } else {
    console.error(`[${label}] Error:`, error);
  }
}

type PaintHeaders = Record<string, string>;
let cachedPaintHeaders: PaintHeaders | null = null;
let inflightHeaders: Promise<PaintHeaders | null> | null = null;

async function resolvePaintHeaders(): Promise<PaintHeaders | null> {
  if (cachedPaintHeaders) return cachedPaintHeaders;
  if (inflightHeaders) return inflightHeaders;

  inflightHeaders = (async () => {
    try {
      const me = await getMyProfile();
      const actorId = me?.data?.userId?.trim();
      if (!actorId) {
        console.warn('[paints] Cannot resolve actor id from /users/me response');
        return null;
      }

      const role = pickRole(me?.data?.roles);
      const headers: PaintHeaders = {
        'Content-Type': 'application/json',
        'X-Internal-Actor-Id': actorId,
        'X-Internal-Actor-Role': role,
      };
      cachedPaintHeaders = headers;
      return headers;
    } catch (err) {
      console.warn('[paints] Failed to load actor info for headers', err);
      return null;
    } finally {
      inflightHeaders = null;
    }
  })();

  return inflightHeaders;
}

function pickRole(roles?: string[]): string {
  if (Array.isArray(roles)) {
    const first = roles.find((r) => typeof r === 'string' && r.trim().length > 0);
    if (first) return first.trim().toUpperCase();
  }
  return 'USER';
}