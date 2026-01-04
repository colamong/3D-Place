import { useCallback, useEffect, useRef, useState } from 'react';
import type { Cartesian3 } from 'cesium';
import type { Voxel } from '@/features/voxels/types';
import { keysFromCenter } from '@/features/voxels/chunk';
import { persistVoxels, restoreVoxels } from '@/features/voxels/persist.local';
import { ZOOM } from '@/features/voxels/constants';

type GetTileBaseHeight = (z:number,x:number,y:number)=>number;

export function useVoxelState(getTileBaseHeight: GetTileBaseHeight, colorRef: { r:number; g:number; b:number }) {
  const [voxels, setVoxels] = useState<Record<string, Voxel>>({});
  const voxelKeySet = useRef(new Set<string>());
  const chunkMap = useRef<Map<string, Set<string>>>(new Map());

  const commitVoxel = useCallback((center: Cartesian3, c?: { r:number; g:number; b:number }) => {
    const { r, g, b } = c ?? colorRef;
    const k = keysFromCenter(center, getTileBaseHeight);
    if (voxelKeySet.current.has(k.gridKey!)) return;

    const id = Math.random().toString(36).slice(2);
    const v: Voxel = {
      id,
      z: k.z,
      tx: k.tx,
      ty: k.ty,
      cx: k.cx,
      cy: k.cy,
      cz: k.ck,
      vx: k.lx,
      vy: k.ly,
      vz: k.lk,
      // legacy
      x: k.tx,
      y: k.ty,
      k: k.ck,
      center,
      r,
      g,
      b,
    };

    setVoxels((prev) => ({ ...prev, [id]: v }));
    voxelKeySet.current.add(k.gridKey!);
    if (!chunkMap.current.has(k.chunkKey!)) chunkMap.current.set(k.chunkKey!, new Set());
    chunkMap.current.get(k.chunkKey!)!.add(id);
    return v;
  }, [getTileBaseHeight, colorRef]);

  const eraseVoxelById = useCallback((id: string) => {
    setVoxels((prev) => {
      const v = prev[id]; if (!v) return prev;
      const next = { ...prev }; delete next[id];

      const { gridKey, chunkKey } = keysFromCenter(v.center, getTileBaseHeight);
      voxelKeySet.current.delete(gridKey);
      const set = chunkMap.current.get(chunkKey);
      if (set) { set.delete(id); if (set.size === 0) chunkMap.current.delete(chunkKey); }
      return next;
    });
  }, [getTileBaseHeight]);

  const clearAllVoxels = useCallback(() => {
    setVoxels({}); voxelKeySet.current.clear(); chunkMap.current.clear();
  }, []);

  const updateVoxelColor = useCallback((id: string, rgb: { r:number; g:number; b:number }) => {
    setVoxels(prev => prev[id] ? ({ ...prev, [id]: { ...prev[id], ...rgb } }) : prev);
  }, []);

  // effects
  useEffect(() => {
    voxelKeySet.current = new Set(
      Object.values(voxels).map(v => keysFromCenter(v.center, getTileBaseHeight).gridKey),
    );
  }, [voxels, getTileBaseHeight]);

  useEffect(() => { const restored = restoreVoxels(); if (Object.keys(restored).length) setVoxels(restored); }, []);
  useEffect(() => { persistVoxels(voxels); }, [voxels]);

  return { voxels, commitVoxel, eraseVoxelById, clearAllVoxels, voxelKeySet, chunkMap, updateVoxelColor };
}
