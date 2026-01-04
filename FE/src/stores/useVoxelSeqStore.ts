import { create } from 'zustand';

export type KeyArgs = {
  z: number;
  tx: number; ty: number;
  cix: number; ciy: number; ciz: number;
  vix: number; viy: number; viz: number;
};

function buildCoordKey({ z, tx, ty, cix, ciy, ciz, vix, viy, viz }: KeyArgs) {
  return `${z}/${tx}/${ty}/${cix}/${ciy}/${ciz}/${vix}/${viy}/${viz}`;
}

interface VoxelSeqState {
  seqs: Record<string, number>;
  // Calculate next vSeq for this coordinate but do not mutate state.
  getNext: (args: KeyArgs) => number;
  // Apply authoritative vSeq from server (UPSERT/UPDATE/ERASE events)
  setFromServer: (args: KeyArgs, vSeq: number) => void;
  // Optionally bump after a successful local commit/ack
  bump: (args: KeyArgs) => number;
}

export const useVoxelSeqStore = create<VoxelSeqState>((set, get) => ({
  seqs: {},
  getNext: (args) => {
    const k = buildCoordKey(args);
    const cur = get().seqs[k] ?? 0;
    return cur + 1;
  },
  setFromServer: (args, vSeq) => {
    if (!Number.isFinite(vSeq)) return;
    const k = buildCoordKey(args);
    set((s) => {
      const cur = s.seqs[k] ?? 0;
      if (vSeq <= cur) return s;
      return { seqs: { ...s.seqs, [k]: vSeq } };
    });
  },
  bump: (args) => {
    const k = buildCoordKey(args);
    let next = 1;
    set((s) => {
      const cur = s.seqs[k] ?? 0;
      next = cur + 1;
      return { seqs: { ...s.seqs, [k]: next } };
    });
    return next;
  },
}));

export { buildCoordKey };
