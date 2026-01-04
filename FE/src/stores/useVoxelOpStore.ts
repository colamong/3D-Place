import { create } from 'zustand';
import type { KeyArgs } from '@/stores/useVoxelSeqStore';
import { buildCoordKey } from '@/stores/useVoxelSeqStore';

interface VoxelOpState {
  lastOpId: Record<string, string | undefined>;
  getLast: (args: KeyArgs) => string | undefined;
  setLast: (args: KeyArgs, opId: string) => void;
  setFromServer: (args: KeyArgs, opId: string) => void;
  clear: (args: KeyArgs) => void;
}

export const useVoxelOpStore = create<VoxelOpState>((set, get) => ({
  lastOpId: {},
  getLast: (args) => get().lastOpId[buildCoordKey(args)],
  setLast: (args, opId) => set((s) => ({
    lastOpId: { ...s.lastOpId, [buildCoordKey(args)]: opId },
  })),
  setFromServer: (args, opId) => set((s) => ({
    lastOpId: { ...s.lastOpId, [buildCoordKey(args)]: opId },
  })),
  clear: (args) => set((s) => {
    const k = buildCoordKey(args);
    const { [k]: _omit, ...rest } = s.lastOpId;
    return { lastOpId: rest };
  }),
}));

