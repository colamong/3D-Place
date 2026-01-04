import { create } from 'zustand';

type OverlayState = {
  gridOn: boolean;
  toggleGrid: () => void;
  mapOn: boolean;
  toggleMap: () => void;
};

export const useOverlayStore = create<OverlayState>((set) => ({
  gridOn: true,
  toggleGrid: () => set((s) => ({ gridOn: !s.gridOn })),
  mapOn: false,
  toggleMap: () => set((s) => ({ mapOn: !s.mapOn })),
}));
