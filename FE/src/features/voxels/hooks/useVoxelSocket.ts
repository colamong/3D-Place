import { useEffect, useRef } from 'react';
import { Cartesian3 } from 'cesium';
import type { Voxel } from '@/features/voxels/types';
import { centerFromChunkIndices } from '@/features/voxels/chunk';
import { useWebSocketStore } from '@/stores/useWebSocketStore';
import { useVoxelSeqStore } from '@/stores/useVoxelSeqStore';
import { useVoxelOpStore } from '@/stores/useVoxelOpStore';  // ✅ 추가
import { CHUNK_N } from '@/features/voxels/constants';
import { rgbToBase64 } from '@/features/voxels/queries/toServerPayload';

type Handlers = {
  pushVoxelsBatch: (v: Voxel[]) => void;
  updateVoxelColor?: (id: string, rgb: { r: number; g: number; b: number }) => void;
  eraseVoxelById?: (id: string) => void;
};

type Deps = {
  getTileBaseHeight: (z: number, x: number, y: number) => number;
  ZOOM: number;
  batchMs?: number;
};

export function useVoxelSocketEvents(url: string | undefined, handlers: Handlers, deps: Deps) {
  const { pushVoxelsBatch, updateVoxelColor, eraseVoxelById } = handlers;
  const { getTileBaseHeight, ZOOM, batchMs = 200 } = deps;

  const connect = useWebSocketStore((s) => s.connect);
  const addListener = useWebSocketStore((s) => s.addListener);

  const bufRef = useRef<Voxel[]>([]);
  const timerRef = useRef<number | null>(null);

  const flush = () => {
    if (timerRef.current) { window.clearTimeout(timerRef.current); timerRef.current = null; }
    const buf = bufRef.current.splice(0);
    if (buf.length) pushVoxelsBatch(buf);
  };

  const decodeRGB = (schema: string | null | undefined, b64: string | null | undefined) => {
    if (!b64) return { r: 255, g: 255, b: 255 };
    try {
      const bin = atob(b64);
      const arr = Uint8Array.from(bin, (c) => c.charCodeAt(0));
      return { r: arr[0] ?? 255, g: arr[1] ?? 255, b: arr[2] ?? 255 };
    } catch {
      return { r: 255, g: 255, b: 255 };
    }
  };

  const eventToVoxel = (ev: any): Voxel | null => {
    try {
      const vix = Number(ev.voxelIndex?.vix);
      const viy = Number(ev.voxelIndex?.viy);
      const viz = Number(ev.voxelIndex?.viz);
      const tx = Number(ev.chunkIndex?.tx);
      const ty = Number(ev.chunkIndex?.ty);
      const cix = Number(ev.chunkIndex?.cix);
      const ciy = Number(ev.chunkIndex?.ciy);
      const ciz = Number(ev.chunkIndex?.ciz);

      if ([vix, viy, viz, cix, ciy, ciz].some((n) => Number.isNaN(n))) return null;

      const tile = { x: tx, y: ty };
      const chunk = { cx: cix, cy: ciy, ck: ciz };
      const local = { lx: vix, ly: viy, lk: viz };

      const center = centerFromChunkIndices({
        tile,
        chunk,
        local,
        getTileBaseHeight,
        zoom: ZOOM,
      });

      const { r, g, b } = decodeRGB(ev.colorSchema, ev.colorBytes);
      const id = `${ZOOM}/${tx}/${ty}/${cix}/${ciy}/${ciz}/${vix}/${viy}/${viz}`;

      return {
        id,
        z: ZOOM,
        tx,      // ✅ 새 필드
        ty,      // ✅ 새 필드
        cx: cix, // ✅ 새 필드
        cy: ciy, // ✅ 새 필드
        cz: ciz, // ✅ 새 필드
        vx: vix, // ✅ 새 필드
        vy: viy, // ✅ 새 필드
        vz: viz, // ✅ 새 필드
        center,
        r,
        g,
        b,
        opId: ev.opId,    // ✅ 추가
        vSeq: ev.vSeq,    // ✅ 추가
      } as Voxel;
    } catch {
      return null;
    }
  };

  useEffect(() => {
    if (url) connect(url);

    const processPkg = (pkg: { globalSeq: number; events: any[] }) => {
      try {
        const events = Array.isArray(pkg?.events) ? pkg.events : [];
        for (const e of events) {
          const op = (e.operationType ?? '').toUpperCase();
          
          // ✅ vSeq + opId 동시에 저장
          try {
            const seqSet = useVoxelSeqStore.getState().setFromServer;
            const setLastOp = useVoxelOpStore.getState().setLast;  // ✅ 추가
            
            const args = {
              z: ZOOM,
              tx: Number(e?.chunkIndex?.tx),
              ty: Number(e?.chunkIndex?.ty),
              cix: Number(e?.chunkIndex?.cix),
              ciy: Number(e?.chunkIndex?.ciy),
              ciz: Number(e?.chunkIndex?.ciz),
              vix: Number(e?.voxelIndex?.vix),
              viy: Number(e?.voxelIndex?.viy),
              viz: Number(e?.voxelIndex?.viz),
            } as const;
            
            if (Object.values(args).every((n) => Number.isFinite(n))) {
              const vSeq = Number(e?.vSeq);
              if (Number.isFinite(vSeq)) {
                seqSet(args, vSeq);
                if (e?.opId) setLastOp(args, e.opId);  // ✅ opId 저장
              }
            }
          } catch { /* noop */ }
          
          if (op === 'ERASE' || op === 'DELETE') {
            const v = eventToVoxel(e);
            try {
              // After erase, reset local vSeq to 0 so next paint starts at 1
              const seqSet = useVoxelSeqStore.getState().setFromServer;
              const opClear = useVoxelOpStore.getState().clear;
              const args = {
                z: ZOOM,
                tx: Number(e?.chunkIndex?.tx),
                ty: Number(e?.chunkIndex?.ty),
                cix: Number(e?.chunkIndex?.cix),
                ciy: Number(e?.chunkIndex?.ciy),
                ciz: Number(e?.chunkIndex?.ciz),
                vix: Number(e?.voxelIndex?.vix),
                viy: Number(e?.voxelIndex?.viy),
                viz: Number(e?.voxelIndex?.viz),
              } as const;
              if (Object.values(args).every((n) => Number.isFinite(n))) {
                seqSet(args, 0);
                opClear(args);
              }
            } catch { /* noop */ }
            if (v && eraseVoxelById) eraseVoxelById(v.id);
            continue;
          }
          
          if (op === 'UPDATE') {
            const v = eventToVoxel(e);
            if (v && updateVoxelColor) updateVoxelColor(v.id, { r: v.r, g: v.g, b: v.b });
            continue;
          }
          
          const v = eventToVoxel(e);
          if (v) bufRef.current.push(v);
        }

        if (!timerRef.current) {
          timerRef.current = window.setTimeout(() => {
            timerRef.current = null;
            flush();
          }, batchMs);
        }
      } catch (err) {
        console.warn('[voxel-ws] parse failed', err);
      }
    };

    const remove = addListener((pkg: { globalSeq: number; events: any[] }) => processPkg(pkg));

    const env: any = (import.meta as any).env || {};
    const forceMock = String(env.VITE_VOXEL_WS_DEV_MOCK ?? 'false').toLowerCase() === 'true';
    const useMock = !!forceMock;
    let mockTimer: number | null = null;
    
    if (useMock) {
      let vSeq = 1;
      const tx = 436, ty = 198;
      const cix = 0, ciy = 0, ciz = 0;
      const makeEvent = (vix: number, viy: number, viz: number, r: number, g: number, b: number) => ({
        opId: crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
        voxelIndex: { vix, viy, viz },
        chunkIndex: { worldName: 'example', tx, ty, cix, ciy, ciz },
        faceMask: 63,
        vSeq: vSeq++,
        colorSchema: 'RGB1',
        colorBytes: rgbToBase64(r, g, b),
        actor: '550e8400-e29b-41d4-a716-446655440000',
        timestamp: new Date().toISOString(),
        operationType: 'UPSERT',
      });
      const colors: [number, number, number][] = [
        [255,110,110],[69,199,232],[91,131,255],[168,121,255],[255,173,41],[140,220,60]
      ];
      const start = () => {
        const tick = () => {
          const [r,g,b] = colors[Math.floor(Math.random()*colors.length)];
          const vix = Math.floor(Math.random() * Math.min(32, CHUNK_N));
          const viy = Math.floor(Math.random() * Math.min(32, CHUNK_N));
          const viz = 0;
          const pkg = { globalSeq: vSeq, events: [makeEvent(vix, viy, viz, r, g, b)] };
          processPkg(pkg);
          mockTimer = window.setTimeout(tick, 400);
        };
        tick();
      };
      start();
    }

    return () => {
      if (timerRef.current) { window.clearTimeout(timerRef.current); timerRef.current = null; }
      if (mockTimer) { window.clearTimeout(mockTimer); mockTimer = null; }
      remove?.();
    };
  }, [url, connect, addListener, pushVoxelsBatch, updateVoxelColor, eraseVoxelById, getTileBaseHeight, ZOOM, batchMs]);
}
