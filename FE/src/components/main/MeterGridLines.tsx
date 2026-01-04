import { useEffect, useRef, useState } from 'react';
import {
  Cartesian2,
  Cartesian3,
  Matrix4,
  SceneTransforms,
  Color,
  Viewer as CesiumViewer,
  PolylineCollection,
  Material,
  Math as CMath,
  Ellipsoid,
} from 'cesium';
import type { Entity as CesiumEntity } from 'cesium';

import { GRID_STEP_M, VOXEL_SIZE_M, GRID_LINE_OFFSET_M } from '@/features/voxels/constants';
import { enuOfRegionByLonLat } from '@/features/voxels/geo';
import { getViewBounds as getViewBoundsPure } from '@/features/voxels/utils/viewBounds';
import { useGetViewer } from '@/features/voxels/hooks/useGetViewer';
import type { ViewerRef } from '@/features/voxels/types';

type Props = {
  viewerRef: ViewerRef;
  color?: Color;
  width?: number;
  enabled?: boolean;
  hideWhileMoving?: boolean;
  maxLines?: number;
  // 보조선은 보류(옵션만 유지)
  majorEvery?: number;
  majorWidth?: number;
  majorColor?: Color;
  lift?: number;
};

type Line = {
  positions: Cartesian3[];
  key: string;
  width: number;
  color: Color;
};

const GRID_ID_PREFIX = '__grid/';

function cleanupGridEntities(v: CesiumViewer) {
  const arr = v.entities.values.slice() as CesiumEntity[];
  for (const e of arr) {
    const name = e.name as string | undefined;
    if (name && name.startsWith(GRID_ID_PREFIX)) v.entities.remove(e);
  }
  v.scene.requestRender?.();
}

function makeToWorld(enu: Matrix4, liftM = 0.08) {
  const scratch = new Cartesian3();
  return (x: number, y: number) =>
    Matrix4.multiplyByPoint(
      enu,
      Cartesian3.fromElements(x, y, liftM, scratch),
      new Cartesian3(),
    );
}

export default function MeterGridLines({
  viewerRef,
  color = Color.WHITE.withAlpha(0.18),
  width = 1,
  enabled = true,
  hideWhileMoving = true,
  maxLines = 512,
  majorEvery = 5,
  majorWidth,
  majorColor,
  lift = 0.5,
}: Props) {
  const [moving, setMoving] = useState(false);
  const debounceRef = useRef<number | null>(null);
  const collRef = useRef<PolylineCollection | null>(null);
  const polyMapRef = useRef<Map<string, any>>(new Map());

  const getViewer = useGetViewer(viewerRef);

  // PolylineCollection 라이프사이클 + 카메라 이동 상태
  useEffect(() => {
    const v = getViewer();
    if (!v) return;

    if (!enabled) {
      const coll = collRef.current;
      if (coll) {
        try {
          v.scene.primitives.remove(coll);
        } catch {}
        collRef.current = null;
      }
      polyMapRef.current.clear();
      cleanupGridEntities(v);
    } else if (!collRef.current) {
      try {
        const coll = v.scene.primitives.add(new PolylineCollection());
        coll.show = true;
        collRef.current = coll;
      } catch {}
    }

    const onStart = () => setMoving(true);
    const onEnd = () => {
      setMoving(false);
      v.scene.requestRender();
    };
    try {
      v.camera.moveStart.addEventListener(onStart);
    } catch {}
    try {
      v.camera.moveEnd.addEventListener(onEnd);
    } catch {}

    return () => {
      try {
        v.camera.moveStart.removeEventListener(onStart);
      } catch {}
      try {
        v.camera.moveEnd.removeEventListener(onEnd);
      } catch {}
      const coll = collRef.current;
      if (coll) {
        try {
          v.scene.primitives.remove(coll);
        } catch {}
        collRef.current = null;
      }
      polyMapRef.current.clear();
      cleanupGridEntities(v);
    };
  }, [enabled, getViewer]);

  useEffect(() => {
    const v = getViewer();
    if (!v) return;

    const recompute = () => {
      const coll = collRef.current;
      if (!enabled || (hideWhileMoving && moving)) {
        if (coll) coll.show = false;
        v.scene.requestRender();
        return;
      }
      if (coll) coll.show = true;

      const cpos = v.camera.positionCartographic;
      if (!cpos) {
        v.scene.requestRender();
        return;
      }

      // 앵커: 화면 중앙에서 지면과의 교차점(피킹 타일)을 사용
      const canvas = v.scene.canvas;
      const centerPx = new Cartesian2(canvas.clientWidth / 2, canvas.clientHeight / 2);
      let lonDeg = CMath.toDegrees(cpos.longitude);
      let latDeg = CMath.toDegrees(cpos.latitude);
      try {
        const ray = v.camera.getPickRay(centerPx);
        if (!ray) throw new Error('no ray');
        const hit = v.scene.globe.pick(ray, v.scene) as Cartesian3 | undefined;
        if (hit) {
          const carto = Ellipsoid.WGS84.cartesianToCartographic(hit);
          lonDeg = CMath.toDegrees(carto.longitude);
          latDeg = CMath.toDegrees(carto.latitude);
        }
      } catch { /* noop */ }
      const { enu: regionEnu, inv: regionInv } = enuOfRegionByLonLat(lonDeg, latDeg);
      // Use z=0 for world-to-cartographic sampling; apply lift only at final positions
      const toWorldReg = makeToWorld(regionEnu, 0);

      // 뷰 사각형 → 타일 범위
      const vb = getViewBoundsPure(v);
      const rect = vb?.rectangleDeg;
      if (!rect) {
        v.scene.requestRender();
        return;
      }
      const { west, south, east, north } = rect;

      // 간격: A안 — 픽셀 기반 스킵 제거, 실제 간격 고정
      const step = GRID_STEP_M ?? VOXEL_SIZE_M;
      const baseOffset = GRID_LINE_OFFSET_M;
      let stepEff = step;

      // Compute single zr ENU bounds by projecting view rectangle to local
      const rectNW = Cartesian3.fromDegrees(west, north, 0);
      const rectNE = Cartesian3.fromDegrees(east, north, 0);
      const rectSW = Cartesian3.fromDegrees(west, south, 0);
      const rectSE = Cartesian3.fromDegrees(east, south, 0);
      const toLocal = (p: Cartesian3) => Matrix4.multiplyByPoint(regionInv, p, new Cartesian3());
      const pts = [rectNW, rectNE, rectSW, rectSE].map(toLocal);
      let xMin = Math.min(...pts.map((p) => p.x));
      let xMax = Math.max(...pts.map((p) => p.x));
      let yMin = Math.min(...pts.map((p) => p.y));
      let yMax = Math.max(...pts.map((p) => p.y));
      const pad = stepEff * 2;
      xMin -= pad; xMax += pad; yMin -= pad; yMax += pad;

      // Limit line density
      const MAX_TOTAL = Math.max(200, Math.min(maxLines ?? 1200, 2000));
      const est = Math.floor((xMax - xMin) / stepEff) + Math.floor((yMax - yMin) / stepEff) + 4;
      if (est > MAX_TOTAL) {
        const factor = Math.max(1, Math.ceil(est / MAX_TOTAL));
        stepEff *= factor;
      }

      if (!collRef.current) return; // 경합 가드

      const newLines: Line[] = [];
      let guard = 0;
      const GUARD_MAX = Math.max(200, Math.min(maxLines ?? 1200, 2000)) + 50;

      const startX = Math.floor((xMin - baseOffset) / stepEff) * stepEff + baseOffset;
      const startY = Math.floor((yMin - baseOffset) / stepEff) * stepEff + baseOffset;

      // Build draped positions over ellipsoid (reduces floating effect)
      const makePositions = (
        kind: 'v' | 'h',
        fixed: number,
        min: number,
        max: number,
      ) => {
        const span = Math.max(1, max - min);
        const segLen = Math.max(stepEff, step, 8);
        const n = Math.max(2, Math.min(64, Math.ceil(span / segLen)));
        const pts: Cartesian3[] = [];
        for (let i = 0; i <= n; i++) {
          const t = i / n;
          const vv = min + t * span;
          const worldOnPlane = kind === 'v' ? toWorldReg(fixed, vv) : toWorldReg(vv, fixed);
          const carto = Ellipsoid.WGS84.cartesianToCartographic(worldOnPlane);
          pts.push(Cartesian3.fromRadians(carto.longitude, carto.latitude, lift));
        }
        return pts;
      };

      for (let x = startX; x <= xMax + 1e-6; x += stepEff) {
        const ix = Math.round((x - baseOffset) / step);
        const vx = ix * step + baseOffset;
        const isMajor = majorEvery > 1 && Math.abs(ix) % majorEvery === 0;
        const w = isMajor ? (majorWidth ?? Math.max(1, width * 1.8)) : width;
        const c = isMajor
          ? majorColor ?? new Color(color.red, color.green, color.blue, Math.min(1, (color.alpha ?? 1) * 1.9))
          : color;
        newLines.push({
          positions: makePositions('v', vx, yMin, yMax),
          key: `zr-vx-${ix}`,
          width: w,
          color: c,
        });
        if (++guard > GUARD_MAX) break;
      }

      for (let y = startY; y <= yMax + 1e-6; y += stepEff) {
        const iy = Math.round((y - baseOffset) / step);
        const vy = iy * step + baseOffset;
        const isMajor = majorEvery > 1 && Math.abs(iy) % majorEvery === 0;
        const w = isMajor ? (majorWidth ?? Math.max(1, width * 1.8)) : width;
        const c = isMajor
          ? majorColor ?? new Color(color.red, color.green, color.blue, Math.min(1, (color.alpha ?? 1) * 1.9))
          : color;
        newLines.push({
          positions: makePositions('h', vy, xMin, xMax),
          key: `zr-vy-${iy}`,
          width: w,
          color: c,
        });
        if (++guard > GUARD_MAX) break;
      }

      // 증분 갱신
      try {
        const coll2 = collRef.current;
        if (!coll2) return;
        const seen = new Set<string>();
        for (const l of newLines) {
          seen.add(l.key);
          let poly = polyMapRef.current.get(l.key);
          if (!poly) {
            poly = coll2.add({
              positions: l.positions,
              width: l.width ?? 1,
              id: `${GRID_ID_PREFIX}${l.key}`,
            });
            try {
              poly.material = Material.fromType('Color', { color: l.color });
            } catch {}
            try {
              polyMapRef.current.set(l.key, poly);
            } catch {}
          } else {
            poly.positions = l.positions;
            poly.width = l.width ?? 1;
            try {
              if (poly.material?.uniforms?.color)
                poly.material.uniforms.color = l.color;
              else
                poly.material = Material.fromType('Color', { color: l.color });
            } catch {}
          }
        }
        for (const [k, poly] of Array.from(polyMapRef.current.entries())) {
          if (!seen.has(k)) {
            try {
              coll2.remove(poly);
            } catch {}
            polyMapRef.current.delete(k);
          }
        }
      } catch {}

      v.scene.requestRender();
    };

    const onChanged = () => {
      if (debounceRef.current) window.clearTimeout(debounceRef.current);
      debounceRef.current = window.setTimeout(recompute, 135);
    };

    recompute();
    v.camera.changed.addEventListener(onChanged);
    return () => {
      v.camera.changed.removeEventListener(onChanged);
      if (debounceRef.current) {
        window.clearTimeout(debounceRef.current);
        debounceRef.current = null;
      }
    };
  }, [
    viewerRef,
    enabled,
    hideWhileMoving,
    moving,
    maxLines,
    getViewer,
    color,
    width,
    majorEvery,
    majorWidth,
    majorColor,
    lift,
  ]);

  return null;
}
