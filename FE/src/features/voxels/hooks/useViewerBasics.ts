import { useCallback, useRef } from 'react';
import { Cartesian2, Cartesian3, Ellipsoid, Math as CMath, Matrix4, Rectangle } from 'cesium';
import type { ViewerRef } from '@/features/voxels/types'; 
import { tileSizeMeters, lonLatToTile } from '@/features/voxels/geo';
import { getViewBounds as getViewBoundsPure } from '@/features/voxels/utils/viewBounds';
import { useGetViewer } from './useGetViewer';

export function useViewerBasics(viewerRef: ViewerRef, BASE_LIFT: number) {
  const baseHMap = useRef<Map<string, number>>(new Map());
  const getViewer = useGetViewer(viewerRef)
  const getTileBaseHeight = useCallback((z: number, x: number, y: number) => {
    const key = `${z}/${x}/${y}`;
    const cached = baseHMap.current.get(key);
    if (cached != null) return cached;

    const { midLon, midLat } = tileSizeMeters(x, y, z);
    const cart = Ellipsoid.WGS84.cartesianToCartographic(
      Cartesian3.fromDegrees(midLon, midLat),
    );
    const base = (cart.height || 0) + BASE_LIFT;
    baseHMap.current.set(key, base);
    return base;
  }, [BASE_LIFT]);

  const unlockCamera = useCallback(() => {
    const v = getViewer(); if (!v) return;
    v.trackedEntity = undefined;
    v.camera.lookAtTransform(Matrix4.IDENTITY);
  }, [getViewer]);

  const logPickAt = useCallback((pos2: Cartesian2, ZOOM: number) => {
    const v = getViewer(); if (!v) return;
    const { scene, camera } = v;
    let world: Cartesian3 | null = null;

    try { if (scene.pickPositionSupported) world = scene.pickPosition(pos2) as Cartesian3 | null; } catch {
        //
    }
    if (!world) {
      const ray = camera.getPickRay(pos2);
      if (ray) world = scene.globe.pick(ray, scene) as Cartesian3 | null;
    }
    if (!world) { console.warn('[PICK] no hit'); return; }

    const carto = Ellipsoid.WGS84.cartesianToCartographic(world);
    const lon = CMath.toDegrees(carto.longitude);
    const lat = CMath.toDegrees(carto.latitude);
    const h = carto.height;
    const { x, y } = lonLatToTile(lon, lat, ZOOM);

    console.groupCollapsed('%c[PICK]', 'color:#0bf');
    console.table({ lon_deg: lon.toFixed(6), lat_deg: lat.toFixed(6), height_m: h.toFixed(2), z: ZOOM, x, y });
    console.log('world (ECEF)', world);
    console.groupEnd();
  }, [getViewer]);

  const getViewBounds = useCallback((): { rectangle?: Rectangle } | null => {
    const v = getViewer(); if (!v) return null;
    return getViewBoundsPure(v);
  }, [getViewer]);

  return { getViewer, getTileBaseHeight, unlockCamera, logPickAt, getViewBounds };
}
