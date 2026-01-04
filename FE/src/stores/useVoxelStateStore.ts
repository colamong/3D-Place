// src/stores/useVoxelStateStore.ts
import { create } from 'zustand';
import type { Voxel } from '@/features/voxels/types';

interface VoxelState {
  voxels: Record<string, Voxel>;
  
  // 단일 추가
  pushVoxel: (voxel: Voxel) => void;
  
  // ✅ 배치 추가 (한 번에 여러 개)
  pushVoxelsBatch: (voxels: Voxel[]) => void;
  
  // 삭제
  eraseVoxelById: (id: string) => void;
  
  // 색상 변경
  updateVoxelColor: (id: string, rgb: { r: number; g: number; b: number }) => void;
  
  // 전체 삭제
  clearAllVoxels: () => void;
}

export const useVoxelStateStore = create<VoxelState>((set) => ({
  voxels: {},
  
  pushVoxel: (voxel) => set((state) => ({
    voxels: {
      ...state.voxels,
      [voxel.id]: voxel,
    },
  })),
  
  // ✅ 배치 추가: 한 번의 set 호출로 여러 voxel 추가
  pushVoxelsBatch: (voxels) => set((state) => {
    const newVoxels = { ...state.voxels };
    voxels.forEach((voxel) => {
      newVoxels[voxel.id] = voxel;
    });
    return { voxels: newVoxels };
  }),
  
  eraseVoxelById: (id) => set((state) => {
    const newVoxels = { ...state.voxels };
    delete newVoxels[id];
    return { voxels: newVoxels };
  }),
  
  updateVoxelColor: (id, rgb) => set((state) => {
    const voxel = state.voxels[id];
    if (!voxel) return state;
    
    return {
      voxels: {
        ...state.voxels,
        [id]: { ...voxel, r: rgb.r, g: rgb.g, b: rgb.b },
      },
    };
  }),
  
  clearAllVoxels: () => set({ voxels: {} }),
}));