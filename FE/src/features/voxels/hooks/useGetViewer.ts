import { useCallback } from 'react';
import type { Viewer as CesiumViewer } from 'cesium';
import type { ViewerRef } from '@/features/voxels/types';

export function useGetViewer(viewerRef: ViewerRef) {
  return useCallback(
    () => viewerRef.current?.cesiumElement as CesiumViewer | undefined,
    [viewerRef],
  );
}
