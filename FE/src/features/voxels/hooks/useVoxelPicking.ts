// src/features/voxels/hooks/useVoxelPicking.ts
import { useCallback } from "react";
import { Cartesian2, Cartesian3, Viewer as CesiumViewer } from "cesium";

type PickedObjectLite = { id?: unknown };
type GetViewer = () => CesiumViewer | undefined;

type PropsBag = { isMock?: boolean; center?: Cartesian3 };
type MaybeEntity = {
  id?: string;
  properties?: { getValue?: () => PropsBag };
};

export function useVoxelPicking(
  getViewer: GetViewer,
  voxels: Record<string, { id: string }>,
  OVERLAY_IDS: Set<string>
) {
  const isOverlayId = useCallback(
    (id?: string) => {
      if (!id) return false;
      for (const base of OVERLAY_IDS) {
        if (id === base || id.startsWith(base + "/")) return true;
      }
      return false;
    },
    [OVERLAY_IDS]
  );

  /** 화면 좌표 → ECEF(지형 우선, 실패 시 depth) */
  const safePickWorld = useCallback(
    (pos2: Cartesian2) => {
      const v = getViewer();
      const scene = v?.scene,
        camera = v?.camera,
        globe = scene?.globe;
      if (!scene || !camera || !globe) return null;

      try {
        const ray = camera.getPickRay(pos2);
        if (ray) {
          const p = globe.pick(ray, scene) as Cartesian3 | null;
          if (p) return p;
        }
      } catch {
        void 0; // no-empty 회피
      }

      try {
        if (scene.pickPositionSupported) {
          const p = scene.pickPosition(pos2) as Cartesian3 | undefined;
          if (p) return p;
        }
      } catch {
        void 0;
      }

      return null;
    },
    [getViewer]
  );

  /** 클릭 히트(월드점) */
  const pickHit = useCallback(
    (pos2: Cartesian2) => {
      const v = getViewer();
      const scene = v?.scene,
        camera = v?.camera,
        globe = scene?.globe;
      if (!scene || !camera || !globe) return null;

      try {
        const ray = camera.getPickRay(pos2);
        if (ray) {
          const p = globe.pick(ray, scene) as Cartesian3 | null;
          if (p) return p;
        }
      } catch {
        void 0;
      }
      try {
        if (scene.pickPositionSupported) {
          const p = scene.pickPosition(pos2) as Cartesian3 | undefined;
          if (p) return p;
        }
      } catch {
        void 0;
      }
      return null;
    },
    [getViewer]
  );

  /** 보셀 또는 mock 엔티티 선택 (drillPick 우선) */
  const pickVoxelEntity = useCallback(
    (pos2: Cartesian2) => {
      const v = getViewer();
      if (!v) return null;
      const scene = v?.scene;
      if (!scene) return null;

      const all = (scene.drillPick
        ? scene.drillPick(pos2)
        : []) as PickedObjectLite[];

      for (const p of all) {
        const raw = p?.id as unknown as MaybeEntity;

        // ✅ 1) GLB mock voxel 먼저 통과
        if (raw?.properties?.getValue?.()?.isMock) {
          // 엔티티에 문자열 id가 있으면 문자열 id로 반환(기존 보셀 처리와 동일하게 동작하도록)
          if (typeof (raw as any) === 'string') return raw as unknown;
          const rawId = (raw as any)?.id;
          if (typeof rawId === 'string') return rawId as unknown;
          return raw as unknown;
        }
        // ✅ 2) 일반 보셀
        const entId = (typeof (raw as any) === "string"
          ? (raw as any)
          : raw?.id) as string | undefined;
        if (entId && !isOverlayId(entId) && voxels[entId]) return raw as unknown;
      }

      // fallback: 단일 pick
      const first = scene.pick(pos2) as PickedObjectLite | undefined;
      const raw1 = first?.id as unknown as MaybeEntity;
      if (raw1?.properties?.getValue?.()?.isMock) {
        if (typeof (raw1 as any) === 'string') return raw1 as unknown;
        const raw1Id = (raw1 as any)?.id;
        if (typeof raw1Id === 'string') return raw1Id as unknown;
        return raw1 as unknown;
      }
      const entId1 = (typeof (raw1 as any) === "string"
        ? (raw1 as any)
        : raw1?.id) as string | undefined;
      if (entId1 && !isOverlayId(entId1) && voxels[entId1])
        return raw1 as unknown;

      return null;
    },
    [getViewer, voxels, isOverlayId]
  );

  return { safePickWorld, pickHit, pickVoxelEntity };
}
