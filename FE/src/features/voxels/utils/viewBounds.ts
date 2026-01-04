import {
  Cartesian2, Cartesian3, Ellipsoid, Rectangle, Viewer as CesiumViewer, Math as CMath
} from 'cesium';

type LLH = { lon: number; lat: number; h: number };

export type ViewBounds = {
  corners: { tl: LLH | null; tr: LLH | null; br: LLH | null; bl: LLH | null };
  rectangle?: Rectangle;  // ✅ 진짜 Cesium.Rectangle (라디안)
  rectangleDeg?: { west: number; south: number; east: number; north: number }; // 참고용
};

export function pickCartographicRobust(v: CesiumViewer, p: Cartesian2) {
  const { scene, camera } = v;
  try {
    if (scene.pickPositionSupported) {
      const c = scene.pickPosition(p) as Cartesian3 | undefined;
      if (c) return Ellipsoid.WGS84.cartesianToCartographic(c);
    }
  } catch {
    /* noop */
  }
  try {
    const ray = camera.getPickRay(p);
    if (ray) {
      const c = scene.globe.pick(ray, scene) as Cartesian3 | null;
      if (c) return Ellipsoid.WGS84.cartesianToCartographic(c);
    }
  } catch {
    /* noop */
  }
  const ec = camera.pickEllipsoid(p, Ellipsoid.WGS84) as Cartesian3 | undefined;
  return ec ? Ellipsoid.WGS84.cartesianToCartographic(ec) : null;
}

export function getViewBounds(v: CesiumViewer): ViewBounds {
  const { scene, camera } = v;
  const w = scene.canvas.clientWidth, h = scene.canvas.clientHeight;
  const pts = [new Cartesian2(0,0), new Cartesian2(w-1,0), new Cartesian2(w-1,h-1), new Cartesian2(0,h-1)];

  const corners = pts.map((p) => {
    const c = pickCartographicRobust(v, p);
    return c
      ? { lon: CMath.toDegrees(c.longitude), lat: CMath.toDegrees(c.latitude), h: c.height }
      : null;
  });

  const tl = corners[0], tr = corners[1], br = corners[2], bl = corners[3];

  const rect = camera.computeViewRectangle(scene.globe.ellipsoid) as Rectangle | undefined;
  const rectangle = rect; // ✅ 라디안 Rectangle 그대로
  const rectangleDeg = rect
    ? {
        west: CMath.toDegrees(rect.west),
        south: CMath.toDegrees(rect.south),
        east: CMath.toDegrees(rect.east),
        north: CMath.toDegrees(rect.north),
      }
    : undefined;

  return {
    corners: { tl, tr, br, bl },
    rectangle,
    rectangleDeg,
  };
}
