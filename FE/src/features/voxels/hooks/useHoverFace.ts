import { useCallback, useState } from 'react';
import { Cartesian2, Cartesian3, Matrix4, Transforms } from 'cesium';
import type { Viewer as CesiumViewer } from 'cesium';

import { pickFaceByRayAABB, chooseDirByLocal } from '@/features/voxels/pick';
import type { HoverFace } from '@/features/voxels/types';

export function useHoverFace({
  getViewer, voxels, VOXEL_SIZE_M, safePickWorld,
}: {
  getViewer: () => CesiumViewer | undefined;
  voxels: Record<string, { center: Cartesian3 }>;
  VOXEL_SIZE_M: number;
  safePickWorld: (p: Cartesian2) => Cartesian3 | null;
}) {
  const [hoverFace, setHoverFace] = useState<HoverFace>(null);

  const handleMove = useCallback((pos2: Cartesian2) => {
    if (!pos2) { setHoverFace(null); return; }
    const v = getViewer(); const scene = v?.scene; if (!scene || !v) return;

    const picked = scene.pick(pos2) as { id?: unknown } | undefined;
    const raw = picked?.id as unknown;
    const voxelId = (() => {
      const x = raw;
      if (typeof x === 'string') return x;
      if (x && typeof x === 'object' && 'id' in x) {
        const id = (x as { id?: unknown }).id;
        return typeof id === 'string' ? id : undefined;
      }
      return undefined;
    })();
    const voxel = voxelId && voxels[voxelId];
    if (!voxel) { setHoverFace(null); return; }

    let dir = pickFaceByRayAABB(v, pos2, voxel.center, VOXEL_SIZE_M);
    if (!dir) {
      const hit = safePickWorld(pos2);
      if (!hit) { setHoverFace(null); return; }
      const enu = Transforms.eastNorthUpToFixedFrame(voxel.center);
      const inv = Matrix4.inverse(enu, new Matrix4());
      const local = Matrix4.multiplyByPoint(inv, hit, new Cartesian3());
      dir = chooseDirByLocal(local, VOXEL_SIZE_M/2, VOXEL_SIZE_M/2, VOXEL_SIZE_M/2);
    }

    setHoverFace({ center: voxel.center, normal: dir, dimX: VOXEL_SIZE_M, dimY: VOXEL_SIZE_M });
  }, [getViewer, voxels, VOXEL_SIZE_M, safePickWorld]);

  return { hoverFace, handleMove };
}
