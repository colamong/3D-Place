import { useCallback } from 'react';
import { Cartesian3 } from 'cesium';
import { snapWorldToVoxelCenter } from '@/features/voxels/utils/snap';
import { CHUNK_N, ZOOM } from '@/features/voxels/constants';
import { centerFromIndex } from '@/features/voxels/chunk';

export function useTestApis({ getTileBaseHeight, pushDraft, commitVoxel, colorRef, VOXEL_SIZE_M, CENTER_OFFSET_M }:{
  getTileBaseHeight: (z:number,x:number,y:number)=>number;
  pushDraft: (d: any)=>void;
  commitVoxel: (c:any, color?:{r:number;g:number;b:number})=>any;
  colorRef: { r:number; g:number; b:number };
  VOXEL_SIZE_M:number;
  CENTER_OFFSET_M:number;
}) {
  const { r, g, b } = colorRef;

  const placeDraftAtECEF = useCallback((x:number,y:number,z:number, snap=true)=>{
    const world = new Cartesian3(x,y,z);
    if (snap) {
      const { center, tx, ty, k } = snapWorldToVoxelCenter(world, { ZOOM, VOXEL_SIZE_M, CENTER_OFFSET_M, getTileBaseHeight });
      pushDraft({ z: ZOOM, x: tx, y: ty, k, center, r, g, b });
    } else {
      pushDraft({ z: ZOOM, x: 0, y: 0, k: 0, center: world, r, g, b });
    }
  }, [r,g,b,pushDraft,getTileBaseHeight,VOXEL_SIZE_M,CENTER_OFFSET_M]);

  const paintAtECEF = useCallback((x:number,y:number,z:number, snap=true)=>{
    const world = new Cartesian3(x,y,z);
    if (snap) {
      const { center } = snapWorldToVoxelCenter(world, { ZOOM, VOXEL_SIZE_M, CENTER_OFFSET_M, getTileBaseHeight });
      commitVoxel(center, { r, g, b });
    } else {
      commitVoxel(world, { r, g, b });
    }
  }, [r,g,b,commitVoxel,getTileBaseHeight,VOXEL_SIZE_M,CENTER_OFFSET_M]);

  const placeDraftAtECEFExact = useCallback((x:number,y:number,z:number)=>{
    const center = new Cartesian3(x,y,z);
    pushDraft({ z: ZOOM, x:0, y:0, k:0, center, r, g, b });
  }, [pushDraft,r,g,b]);

  const paintAtECEFExact = useCallback((x:number,y:number,z:number)=>{
    commitVoxel(new Cartesian3(x,y,z), { r, g, b });
  }, [commitVoxel,r,g,b]);

  const placeDraftAtChunkCell = useCallback((tx:number,ty:number,cix:number,ciy:number,ck:number,cell_ix:number,cell_iy:number,cell_k:number)=>{
    const ix = cix*CHUNK_N + cell_ix;
    const iy = ciy*CHUNK_N + cell_iy;
    const k  = ck *CHUNK_N + cell_k;
    const center = centerFromIndex({ tile: { x: tx, y: ty }, ix, iy, k }, getTileBaseHeight);
    pushDraft({ z: ZOOM, x: tx, y: ty, k, center, r, g, b });
  }, [getTileBaseHeight,pushDraft,r,g,b]);

  const paintAtChunkCell = useCallback((tx:number,ty:number,cix:number,ciy:number,ck:number,cell_ix:number,cell_iy:number,cell_k:number)=>{
    const ix = cix*CHUNK_N + cell_ix;
    const iy = ciy*CHUNK_N + cell_iy;
    const k  = ck *CHUNK_N + cell_k;
    const center = centerFromIndex({ tile: { x: tx, y: ty }, ix, iy, k }, getTileBaseHeight);
    commitVoxel(center, { r, g, b });
  }, [getTileBaseHeight,commitVoxel,r,g,b]);

  return {
    placeDraftAtECEF, paintAtECEF,
    placeDraftAtECEFExact, paintAtECEFExact,
    placeDraftAtChunkCell, paintAtChunkCell,
  };
}
