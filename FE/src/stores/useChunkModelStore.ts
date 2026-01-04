import { create } from 'zustand';

import type { ChunkModelSpec } from '@/features/voxels/types';

type ChunkModelState = {
  items: ChunkModelSpec[];
  setItems: (items: ChunkModelSpec[]) => void;
  upsertItem: (item: ChunkModelSpec) => void;
  removeItem: (id: string) => void;
  clear: () => void;
};

export const useChunkModelStore = create<ChunkModelState>()((set) => ({
  items: [],
  setItems: (items) => set({ items }),
  upsertItem: (item) =>
    set((state) => {
      const idx = state.items.findIndex((it) => it.id === item.id);
      if (idx === -1) return { items: [...state.items, item] };
      const next = state.items.slice();
      next[idx] = item;
      return { items: next };
    }),
  removeItem: (id) =>
    set((state) => ({
      items: state.items.filter((it) => it.id !== id),
    })),
  clear: () => set({ items: [] }),
}));
