import { Cartesian3, Matrix4, Ellipsoid, Math as CMath } from 'cesium';
import { lonLatToTile, enuOfRegionByLonLat } from '@/features/voxels/geo';
import { keysFromCenter } from '@/features/voxels/chunk';

type GetTileBaseHeight = (z:number,x:number,y:number)=>number;

export function snapWorldToVoxelCenter(
  world: Cartesian3,
  { ZOOM, VOXEL_SIZE_M, CENTER_OFFSET_M, getTileBaseHeight }:
  { ZOOM:number; VOXEL_SIZE_M:number; CENTER_OFFSET_M:number; getTileBaseHeight:GetTileBaseHeight }
) {
  const step = VOXEL_SIZE_M, base = CENTER_OFFSET_M;

  const carto = Ellipsoid.WGS84.cartesianToCartographic(world);
  const lon = CMath.toDegrees(carto.longitude);
  const lat = CMath.toDegrees(carto.latitude);

  // Region ENU for snapping
  const { enu, inv } = enuOfRegionByLonLat(lon, lat);
  const local = Matrix4.multiplyByPoint(inv, world, new Cartesian3());
  const ix = Math.round((local.x - base) / step);
  const iy = Math.round((local.y - base) / step);
  const snapX = ix * step + base;
  const snapY = iy * step + base;

  const xy = Matrix4.multiplyByPoint(enu, new Cartesian3(snapX, snapY, 0), new Cartesian3());
  const cartoXY = Ellipsoid.WGS84.cartesianToCartographic(xy);
  const lonXY = CMath.toDegrees(cartoXY.longitude);
  const latXY = CMath.toDegrees(cartoXY.latitude);
  const { x: tx, y: ty } = lonLatToTile(lonXY, latXY, ZOOM);
  const baseH = getTileBaseHeight(ZOOM, tx, ty);
  const worldH = Number.isFinite(carto.height) ? carto.height : baseH;
  const k = Math.max(0, Math.round((worldH - baseH) / step - 0.5));
  const h = baseH + (k + 0.5) * step;

  const center = Cartesian3.fromRadians(cartoXY.longitude, cartoXY.latitude, h);

  const { gridKey, chunkKey } = keysFromCenter(center, getTileBaseHeight);
  return { center, tx, ty, ix, iy, k, gridKey, chunkKey }

;
}

