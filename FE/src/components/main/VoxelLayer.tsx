import { memo, useEffect, useRef } from "react";
import { Entity, CustomDataSource } from "resium";
import { Cartesian3, Color, Ellipsoid, DataSourceCollection } from "cesium";
import type { Voxel } from "@/features/voxels/types";
import { VOXEL_SIZE_M, VOXEL_SINK_M, VOXEL_ALPHA } from "@/features/voxels/constants";

/**
 * ✅ 안정 + 최적화 버전
 * - Cesium primitive를 매번 새로 안 만들고
 * - voxels가 실제로 바뀌었을 때만 리렌더 트리거
 * - memo 비교: voxel 개수, 색상, 위치 변화만 감지
 */
function VoxelLayerComp({
  voxels,
  mapOn,
}: {
  voxels: Record<string, Voxel>;
  mapOn: boolean;
}) {
  const prevKeysRef = useRef<string[]>([]);
  const prevCount = prevKeysRef.current.length;
  const curKeys = Object.keys(voxels);
  const curCount = curKeys.length;

  useEffect(() => {
    prevKeysRef.current = curKeys;
  }, [curKeys.join(",")]);

  return (
    <>
      {curKeys.map((key) => {
        const v = voxels[key];
        if (!v) return null;

        const alpha = mapOn ? VOXEL_ALPHA : 255;
        const mat = Color.fromBytes(v.r, v.g, v.b, alpha);

        const carto = Ellipsoid.WGS84.cartesianToCartographic(v.center);
        const pos = Cartesian3.fromRadians(
          carto.longitude,
          carto.latitude,
          carto.height - VOXEL_SINK_M
        );

        return (
          <Entity
            key={v.id}
            id={v.id}
            position={pos}
            box={{
              dimensions: new Cartesian3(
                VOXEL_SIZE_M,
                VOXEL_SIZE_M,
                VOXEL_SIZE_M
              ),
              material: mat,
              outline: false,
            }}
          />
        );
      })}
    </>
  );
}

// ✅ memo 비교 로직 — “필요할 때만 리렌더”
export default memo(VoxelLayerComp, (prev, next) => {
  // mapOn 바뀌면 리렌더
  if (prev.mapOn !== next.mapOn) return false;

  const prevKeys = Object.keys(prev.voxels);
  const nextKeys = Object.keys(next.voxels);

  // 개수 다르면 리렌더
  if (prevKeys.length !== nextKeys.length) return false;

  // key set이 달라졌으면 리렌더
  if (prevKeys.join(",") !== nextKeys.join(",")) return false;

  // 색상 또는 좌표 달라졌으면 리렌더
  for (const k of nextKeys) {
    const pv = prev.voxels[k];
    const nv = next.voxels[k];
    if (!pv || !nv) return false;
    if (
      pv.r !== nv.r ||
      pv.g !== nv.g ||
      pv.b !== nv.b ||
      pv.center?.x !== nv.center?.x ||
      pv.center?.y !== nv.center?.y ||
      pv.center?.z !== nv.center?.z
    )
      return false;
  }

  return true; // 완전히 동일하면 리렌더 스킵
});