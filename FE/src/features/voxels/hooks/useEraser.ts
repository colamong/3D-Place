import { useCallback, useEffect, useState } from 'react';
import { Cartesian2, Viewer as CesiumViewer } from 'cesium';
type PickedObjectLite = { id?: unknown };

export function useEraser({
  ERASER_MAX_CHARGES, ERASER_REGEN_INTERVAL_MS, getViewer, OVERLAY_IDS, removeDraftById, eraseVoxelById,
}: {
  ERASER_MAX_CHARGES:number;
  ERASER_REGEN_INTERVAL_MS:number;
  getViewer:()=>CesiumViewer|undefined;
  OVERLAY_IDS:Set<string>;
  removeDraftById:(id:string)=>void;
  eraseVoxelById:(id:string)=>void;
}) {
  const [eraseMode, setEraseMode] = useState(false);
  const [eraserCharges, setEraserCharges] = useState(ERASER_MAX_CHARGES);
  const [eraserRegenLeftMs, setEraserRegenLeftMs] = useState(0);

  useEffect(() => {
    if (eraserCharges >= ERASER_MAX_CHARGES) { setEraserRegenLeftMs(0); return; }
    const started = Date.now(), nextAt = started + ERASER_REGEN_INTERVAL_MS;
    const id = window.setInterval(() => {
      const left = nextAt - Date.now();
      setEraserRegenLeftMs(Math.max(0, left));
      if (left <= 0) { window.clearInterval(id); setEraserCharges((c)=>Math.min(ERASER_MAX_CHARGES, c+1)); }
    }, 250);
    return () => window.clearInterval(id);
  }, [eraserCharges, ERASER_MAX_CHARGES, ERASER_REGEN_INTERVAL_MS]);

  const eraseAnyAt = useCallback((pos2: Cartesian2) => {
    const v = getViewer(); const s = v?.scene; if (!s) return;
    const picks = (s.drillPick ? s.drillPick(pos2) : []) as PickedObjectLite[];
    const ids = picks.map((p)=>{ const raw = (p?.id as any); return (typeof raw === 'string' ? raw : raw?.id) as string|undefined; })
      .filter(Boolean) as string[];

    const ghost = ids.find((s)=>s.startsWith('__ghost/'));
    if (ghost) { removeDraftById(ghost.replace('__ghost/','')); return; }

    const vid = ids.find((id)=>!OVERLAY_IDS.has(id));
    if (vid) eraseVoxelById(vid);
  }, [getViewer, removeDraftById, eraseVoxelById, OVERLAY_IDS]);

  return { eraseMode, setEraseMode, eraserCharges, setEraserCharges, eraserRegenLeftMs, eraseAnyAt };
}
