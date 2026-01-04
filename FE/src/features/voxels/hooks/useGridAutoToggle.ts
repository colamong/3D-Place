import { useEffect, useRef, useState } from 'react';
import * as Cesium from 'cesium';
import type { CesiumComponentRef } from 'resium';
import type { Viewer as CesiumViewer } from 'cesium';
import type { ViewerRef } from '@/features/voxels/types';
import { GRID_ENABLE_HEIGHT_M, GRID_DISABLE_HEIGHT_M, GRID_DISABLE_PX, GRID_ENABLE_PX, VOXEL_SIZE_M } from '@/features/voxels/constants';

/**
 * Auto-toggle grid visibility availability based on camera height or pixels-per-voxel.
 * Returns `gridDisabled` so UI can disable the toggle button when grid is not appropriate.
 */
export function useGridAutoToggle(
  viewerRef: ViewerRef,
  showGrid: boolean,
  setShowGrid: (on: boolean) => void,
) {
  const [gridDisabled, setGridDisabled] = useState(true);
  const gridPivotPxRef = useRef<number | null>(null);
  const gridPivotHeightRef = useRef<number | null>(null);

  useEffect(() => {
    let mounted = true;
    let attached = false as boolean;
    let v: CesiumViewer | undefined;
    let onChanged: (() => void) | undefined;
    let onMoveStart: (() => void) | undefined;
    let onMoveEnd: (() => void) | undefined;

    const update = () => {
      if (!v) return;

      const cpos = v.camera?.positionCartographic;
      const scene = v.scene;
      const dist = (a?: Cesium.Cartesian2, b?: Cesium.Cartesian2) =>
        !a || !b ? 0 : Math.hypot(a.x - b.x, a.y - b.y);
      let pxPerM = 0;
      try {
        const centerECEF = Cesium.Cartesian3.fromRadians(
          cpos?.longitude ?? 0,
          cpos?.latitude ?? 0,
          0,
        );
        const enu = Cesium.Transforms.eastNorthUpToFixedFrame(centerECEF);
        const toWorld = (x: number, y: number) =>
          Cesium.Matrix4.multiplyByPoint(
            enu,
            Cesium.Cartesian3.fromElements(x, y, 0.5, new Cesium.Cartesian3()),
            new Cesium.Cartesian3(),
          );
        const p0 = Cesium.SceneTransforms.worldToWindowCoordinates(
          scene,
          toWorld(0, 0),
        );
        const p1 = Cesium.SceneTransforms.worldToWindowCoordinates(
          scene,
          toWorld(1, 0),
        );
        const p2 = Cesium.SceneTransforms.worldToWindowCoordinates(
          scene,
          toWorld(0, 1),
        );
        pxPerM = Math.max(dist(p0, p1), dist(p0, p2));
      } catch {
        pxPerM = 0;
      }

      const h = v.camera?.positionCartographic?.height ?? Number.POSITIVE_INFINITY;

      const currDisabled = gridDisabled;
      let nextDisabled = currDisabled;
      const pxPerVoxel = pxPerM * VOXEL_SIZE_M;
      if (pxPerM > 0) {
        if (currDisabled) {
          if (pxPerVoxel >= GRID_ENABLE_PX) {
            gridPivotPxRef.current = pxPerVoxel;
            gridPivotHeightRef.current = h;
            nextDisabled = false;
          } else {
            nextDisabled = true;
          }
        } else {
          const pivot = gridPivotPxRef.current ?? GRID_DISABLE_PX;
          nextDisabled = pxPerVoxel < pivot;
        }
      } else {
        if (currDisabled) {
          if (h < GRID_ENABLE_HEIGHT_M) {
            gridPivotHeightRef.current = h;
            nextDisabled = false;
          } else {
            nextDisabled = true;
          }
        } else {
          const pivotH = gridPivotHeightRef.current ?? GRID_DISABLE_HEIGHT_M;
          nextDisabled = h >= pivotH;
        }
      }

      if (nextDisabled && showGrid) setShowGrid(false);
      setGridDisabled(nextDisabled);
    };

    const tryAttach = () => {
      if (!mounted) return;
      v = viewerRef.current?.cesiumElement as CesiumViewer | undefined;
      if (!v) {
        requestAnimationFrame(tryAttach);
        return;
      }
      if (attached) return;
      attached = true;
      update();
      onChanged = () => update();
      onMoveStart = () => update();
      onMoveEnd = () => update();
      try { v.camera.changed.addEventListener(onChanged); } catch {}
      try { v.camera.moveStart.addEventListener(onMoveStart); } catch {}
      try { v.camera.moveEnd.addEventListener(onMoveEnd); } catch {}
    };

    tryAttach();

    return () => {
      mounted = false;
      if (v) {
        try { if (onChanged) v.camera.changed.removeEventListener(onChanged); } catch {}
        try { if (onMoveStart) v.camera.moveStart.removeEventListener(onMoveStart); } catch {}
        try { if (onMoveEnd) v.camera.moveEnd.removeEventListener(onMoveEnd); } catch {}
      }
    };
  }, [viewerRef, showGrid, setShowGrid, gridDisabled]);

  return gridDisabled;
}

