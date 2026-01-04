import { create } from 'zustand';

type AnchorMap = Map<string, React.RefObject<HTMLElement>>;

type AnchorState = {
  anchors: AnchorMap;
  register: (key: string, ref: React.RefObject<HTMLElement>) => void;
  unregister: (key: string) => void;
};

export const useAnchorStore = create<AnchorState>((set) => ({
  anchors: new Map(),
  register: (key, ref) =>
    set((s) => {
      const next = new Map(s.anchors);
      next.set(key, ref);
      return { anchors: next };
    }),
  unregister: (key) =>
    set((s) => {
      const next = new Map(s.anchors);
      next.delete(key);
      return { anchors: next };
    }),
}));

// 헬퍼 훅
export function useAnchor(key: string) {
  return useAnchorStore((s) => s.anchors.get(key) ?? null);
}
