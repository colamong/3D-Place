import { Cartesian3 } from 'cesium';

export type MockVoxelGrid = {
  z: number;
  tile: { x: number; y: number };
  ix: number;
  iy: number;
  k: number;
};

export type MockVoxelEntry = {
  id: string;
  center: Cartesian3;
  grid?: MockVoxelGrid;
};

type RegistryItem = MockVoxelEntry & { specId: string };

const REGISTRY = new Map<string, RegistryItem>();
const SPEC_INDEX = new Map<string, Set<string>>();

export function setMockVoxelsForSpec(specId: string, entries: MockVoxelEntry[]): void {
  clearMockVoxelsForSpec(specId);
  if (!entries.length) return;

  const ids = new Set<string>();
  entries.forEach((entry) => {
    REGISTRY.set(entry.id, { ...entry, specId });
    ids.add(entry.id);
  });
  SPEC_INDEX.set(specId, ids);
}

export function clearMockVoxelsForSpec(specId: string): void {
  const ids = SPEC_INDEX.get(specId);
  if (!ids) return;
  ids.forEach((id) => REGISTRY.delete(id));
  SPEC_INDEX.delete(specId);
}

export function getMockVoxelById(id: string): RegistryItem | undefined {
  return REGISTRY.get(id);
}
