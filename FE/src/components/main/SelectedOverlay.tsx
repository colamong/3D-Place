import { memo } from 'react';
import { Entity } from 'resium';
import { Cartesian3, Color, Ellipsoid } from 'cesium';
import type { Voxel } from '@/features/voxels/types';
import { VOXEL_SIZE_M, VOXEL_SINK_M } from '@/features/voxels/constants';

export default memo(function SelectedOverlay({
  voxels,
  selectedIds,
}: {
  voxels: Record<string, Voxel>;
  selectedIds: string[];
}) {
  if (!selectedIds?.length) return null;
  return (
    <>
      {selectedIds.map((id) => {
        const v = voxels[id];
        if (!v) return null;
        const carto = Ellipsoid.WGS84.cartesianToCartographic(v.center);
        // 약간 크게/약간 위로 올려서 z-fighting 방지
        const pos = Cartesian3.fromRadians(carto.longitude, carto.latitude, carto.height - VOXEL_SINK_M + 0.1);
        return (
          <Entity
            key={`sel-${id}`}
            id={`__overlay_highlight/${id}`}
            position={pos}
            box={{
              dimensions: new Cartesian3(VOXEL_SIZE_M * 1.02, VOXEL_SIZE_M * 1.02, VOXEL_SIZE_M * 1.02),
              fill: false,
              outline: true,
              outlineColor: Color.YELLOW,
            }}
          />
        );
      })}
    </>
  );
});
