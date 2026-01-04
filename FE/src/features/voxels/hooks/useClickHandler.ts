import { useCallback } from 'react';
import { Cartesian2 } from 'cesium';
import type { Viewer as CesiumViewer } from 'cesium';
import type { DraftVoxel } from '@/features/voxels/types';

type PickedObjectLite = { id?: unknown };

export function useClickHandler({
  getViewer,
  eraseMode, eraserCharges, setEraserCharges,
  drafts, pickVoxelEntity,
  makeDraftOnGround, makeDraftOnFace, makeDraftNextToDraft,
  removeDraftById, eraseVoxelById,
}: {
  getViewer: () => CesiumViewer | undefined;
  eraseMode: boolean;
  eraserCharges: number;
  setEraserCharges: (updater: (c:number)=>number) => void;
  drafts: DraftVoxel[];
  pickVoxelEntity: (p: Cartesian2) => unknown | null;
  makeDraftOnGround: (p: Cartesian2) => void;
  makeDraftOnFace: (p: Cartesian2) => boolean;
  makeDraftNextToDraft: (d: DraftVoxel, p: Cartesian2) => boolean;
  removeDraftById: (id: string) => void;
  eraseVoxelById: (id: string) => void;
}) {
  return useCallback((pos2: Cartesian2 | undefined) => {
    if (!pos2) return;

    // Eraser mode: left-click deletes (drafts unlimited, voxels with charge)
    if (eraseMode) {
      const v = getViewer(); const scene = v?.scene; if (!scene) return;
      const picks = (scene.drillPick ? scene.drillPick(pos2) : []) as PickedObjectLite[];
      const ids = picks.map(p => {
        const raw = p?.id as unknown;
        if (typeof raw === 'string') return raw;
        if (raw && typeof raw === 'object' && 'id' in (raw as object)) {
          const id = (raw as { id?: unknown }).id;
          return typeof id === 'string' ? id : undefined;
        }
        return undefined;
      }).filter((s): s is string => typeof s === 'string');

      const ghost = ids.find(s => s.startsWith('__ghost/'));
      if (ghost) { removeDraftById(ghost.replace('__ghost/','')); return; }

      if (eraserCharges > 0) {
        const ent = pickVoxelEntity(pos2);
        const voxelId: string | undefined = (() => {
          if (typeof ent === 'string') return ent;
          const id = (ent as { id?: unknown })?.id;
          return typeof id === 'string' ? id : undefined;
        })();
        if (voxelId) { eraseVoxelById(voxelId); setEraserCharges(c => Math.max(0, c-1)); }
      }
      return;
    }

    // ghost 인접
    {
      const v = getViewer(); const scene = v?.scene;
      if (scene) {
        const picks = (scene.drillPick ? scene.drillPick(pos2) : []) as PickedObjectLite[];
        const ids = picks.map(p => {
        const raw = p?.id as unknown;
        if (typeof raw === 'string') return raw;
        if (raw && typeof raw === 'object' && 'id' in (raw as object)) {
        const id = (raw as { id?: unknown }).id;
        return typeof id === 'string' ? id : undefined;
        }
        return undefined;
        }).filter((s): s is string => typeof s === 'string');
        
        const ghost = ids.find(s => s.startsWith('__ghost/'));
        if (ghost) {
          const did = ghost.replace('__ghost/','');
          const base = drafts.find(d => d.id === did);
          if (base) { makeDraftNextToDraft(base, pos2); return; }
        }
      }
    }

    // 보셀 면
    if (pickVoxelEntity(pos2)) { if (makeDraftOnFace(pos2)) return; }
    makeDraftOnGround(pos2);
  }, [
    getViewer, eraseMode, eraserCharges, setEraserCharges,
    drafts, pickVoxelEntity, makeDraftOnGround, makeDraftOnFace,
    makeDraftNextToDraft, removeDraftById, eraseVoxelById,
  ]);
}
