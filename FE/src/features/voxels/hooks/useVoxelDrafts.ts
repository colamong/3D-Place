import { useCallback, useEffect, useRef, useState } from 'react';
import { Cartesian3 } from 'cesium';
import { keysFromCenter } from '../chunk';
import type { DraftVoxel, Voxel } from '../types';

type GetTileBaseHeight = (z:number,x:number,y:number)=>number;
const GHOST_PREFIX = '__ghost/';

export function useVoxelDrafts(
  deps: {
    MAX_DRAFTS: number;
    getTileBaseHeight: GetTileBaseHeight;
    openColorToast: () => void;
    commitVoxel: (center: Cartesian3, color?: { r:number; g:number; b:number }) => Voxel | undefined;
    voxelKeySet: React.MutableRefObject<Set<string>>; // 중복 방지
    defaultColor: { r:number; g:number; b:number };
  }
) {
  const { MAX_DRAFTS, getTileBaseHeight, openColorToast, commitVoxel, voxelKeySet, defaultColor } = deps;

  const [drafts, setDrafts] = useState<DraftVoxel[]>([]);
  const [draftLimitHit, setDraftLimitHit] = useState(false);
  const draftKeySet = useRef(new Set<string>());

  useEffect(() => {
    draftKeySet.current = new Set(
      drafts.map((d) => keysFromCenter(d.center, getTileBaseHeight).gridKey),
    );
  }, [drafts, getTileBaseHeight]);

  const pushDraft = useCallback((d: Omit<DraftVoxel, 'id'>) => {
    const { gridKey } = keysFromCenter(d.center, getTileBaseHeight);
    if (draftKeySet.current.has(gridKey) || voxelKeySet.current.has(gridKey)) return;

    setDrafts((prev) => {
      if (prev.length >= MAX_DRAFTS) {
        setDraftLimitHit(true);
        setTimeout(() => setDraftLimitHit(false), 1600);
        return prev;
      }
      const id = Math.random().toString(36).slice(2);
      draftKeySet.current.add(gridKey);
      return [...prev, { id, ...d }];
    });
    openColorToast();
  }, [getTileBaseHeight, MAX_DRAFTS, openColorToast, voxelKeySet]);

  const removeDraftById = useCallback((id: string) => {
    setDrafts((prev) => {
      const target = prev.find((d) => d.id === id);
      if (target) {
        const { gridKey } = keysFromCenter(target.center, getTileBaseHeight);
        draftKeySet.current.delete(gridKey);
      }
      return prev.filter((d) => d.id !== id);
    });
  }, [getTileBaseHeight]);

  const commitAllDrafts = useCallback(() => {
    const committed: Voxel[] = [];
    for (const d of drafts) {
      const v = commitVoxel(d.center, { r: d.r, g: d.g, b: d.b });
      if (v) committed.push(v);
    }
    setDrafts([]); 
    return committed;
  }, [drafts, commitVoxel]);


  const commitSomeDrafts = useCallback((maxCount: number) => {
  if (maxCount <= 0) return [] as Voxel[];

  const draftsToCommit = drafts.slice(0, maxCount);
  const remain = drafts.slice(maxCount);

  const committed: Voxel[] = [];
  for (const d of draftsToCommit) {
    const v = commitVoxel(d.center, { r: d.r, g: d.g, b: d.b });
    if (v) committed.push(v);
  }

  setDrafts(remain);

  return committed;
}, [drafts, commitVoxel]);

  const clearDrafts = useCallback(() => {
    setDrafts([]);
  }, []);

  // 유틸: 픽 결과에서 고스트 드래프트인지 확인
  const findGhostDraftFromIds = useCallback((ids: string[]) => {
    const ghost = ids.find((s) => s.startsWith(GHOST_PREFIX));
    if (!ghost) return null;
    const did = ghost.replace(GHOST_PREFIX, '');
    return drafts.find((x) => x.id === did) ?? null;
  }, [drafts]);

  return {
    drafts, draftLimitHit, draftKeySet,
    pushDraft, removeDraftById, commitAllDrafts, commitSomeDrafts, clearDrafts,
    findGhostDraftFromIds,
    defaultColor, // 편의상 노출
  };
}
