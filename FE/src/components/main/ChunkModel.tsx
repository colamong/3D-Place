import { useMemo } from 'react';
import { Model } from 'resium';
import * as Cesium from 'cesium';
import type { Matrix4 } from 'cesium';
import { buildChunkModelMatrix } from '@/features/voxels/chunk';
import type { ChunkModelSpec, GetTileBaseHeight } from '@/features/voxels/types';
import { VOXEL_SIZE_M } from '@/features/voxels/constants';

type Props = {
  spec: ChunkModelSpec;
  getTileBaseHeight: GetTileBaseHeight;
  onReady?: (m: any) => void;
  onError?: (e: unknown) => void;
};

export default function ChunkModel({ spec, getTileBaseHeight, onReady, onError }: Props) {
  const matrix = useMemo<Matrix4>(() => {
    const m = buildChunkModelMatrix({
      tile: spec.tile,
      chunk: spec.chunk,
      getTileBaseHeight,
      origin: spec.origin ?? 'cell',
      unitScale: spec.unitScale ?? VOXEL_SIZE_M,
      localOffset: spec.localOffset,
      zoom: spec.zoom,
    });
    try {
      const t = Cesium.Matrix4.getTranslation(m, new Cesium.Cartesian3());
      const carto = Cesium.Ellipsoid.WGS84.cartesianToCartographic(t);
      // eslint-disable-next-line no-console
      console.info('[ChunkModel] matrix ready', {
        tile: spec.tile,
        chunk: spec.chunk,
        origin: spec.origin ?? 'cell',
        unitScale: spec.unitScale ?? VOXEL_SIZE_M,
        localOffset: spec.localOffset,
        lon: Cesium.Math.toDegrees(carto.longitude),
        lat: Cesium.Math.toDegrees(carto.latitude),
        h: carto.height,
      });
    } catch {}
    return m;
  }, [
    spec.tile.x,
    spec.tile.y,
    spec.chunk.cx,
    spec.chunk.cy,
    spec.chunk.ck,
    spec.origin,
    spec.unitScale,
    spec.localOffset?.x,
    spec.localOffset?.y,
    spec.localOffset?.z,
    spec.zoom,
    getTileBaseHeight,
  ]);

  return (
    <Model
      url={spec.url}
      modelMatrix={matrix}
      allowPicking={spec.allowPicking ?? false}
      debugShowBoundingVolume={!!spec.debug}
      minimumPixelSize={0}
      maximumScale={Number.POSITIVE_INFINITY}
      onReady={onReady}
      onError={onError}
    />
  );
}

