import { memo } from "react";
import { Entity } from "resium";
import { Cartesian3, Color, Ellipsoid } from "cesium";
import type { DraftVoxel } from "@/features/voxels/types";
import { VOXEL_SIZE_M, VOXEL_SINK_M } from '@/features/voxels/constants';

function GhostLayerComp({ drafts }: { drafts: DraftVoxel[] }) {
  if (!drafts?.length) return null;
  return (
    <>
      {drafts.map(d => {
        const carto = Ellipsoid.WGS84.cartesianToCartographic(d.center);
        const pos = Cartesian3.fromRadians(carto.longitude, carto.latitude, carto.height - VOXEL_SINK_M);
        return (
          <Entity
            key={d.id}
            id={`__ghost/${d.id}`}
            position={pos}
            box={{
              dimensions: new Cartesian3(VOXEL_SIZE_M, VOXEL_SIZE_M, VOXEL_SIZE_M),
              material: Color.fromBytes(d.r, d.g, d.b, 120), // 반투명
              outline: true,
              outlineColor: Color.BLACK.withAlpha(0.6),
            }}
          />
        );
      })}
    </>
  );
}

// Memoize to avoid re-render flicker when unrelated state updates
export default memo(GhostLayerComp, (prev, next) => prev.drafts === next.drafts);