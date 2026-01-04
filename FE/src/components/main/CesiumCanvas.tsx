import { useEffect, useCallback } from 'react';
import { Viewer } from 'resium';
import * as Cesium from 'cesium';
import type { CesiumComponentRef } from 'resium';
import type { Viewer as CesiumViewer } from 'cesium';
import { ensureGlobalEnuByLonLat } from '@/features/voxels/geo';

type Props = {
  viewerRef: React.MutableRefObject<CesiumComponentRef<CesiumViewer> | null>;
  terrainProvider?: Cesium.TerrainProvider | undefined;
  children?: React.ReactNode;
};

export default function CesiumCanvas({
  viewerRef,
  terrainProvider,
  children,
}: Props) {
  const tune = useCallback(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;

    v.scene.globe.depthTestAgainstTerrain = false;
    v.useBrowserRecommendedResolution = true;
    v.scene.requestRenderMode = true;
    v.scene.maximumRenderTimeChange = 0.5;
    v.resolutionScale = window.devicePixelRatio;

    try {
      v.cesiumWidget?.screenSpaceEventHandler?.removeInputAction(
        Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK,
      );
    } catch {
      //
    }
    try {
      v.screenSpaceEventHandler?.removeInputAction(
        Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK,
      );
      v.screenSpaceEventHandler?.setInputAction(
        () => {},
        Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK,
      );
    } catch {
      //
    }

    // Also ensure the camera controller mappings are applied and stick
    try {
      const applyControls = () => {
        const ssc = v.scene.screenSpaceCameraController;
        if (!ssc) return;

        // 기능 on/off
        ssc.enableRotate = false;
        ssc.enableTilt = false;
        ssc.enableTranslate = true;
        ssc.enableLook = true;

        // 입력 매핑
        ssc.translateEventTypes = [Cesium.CameraEventType.LEFT_DRAG];
        ssc.zoomEventTypes = [
          Cesium.CameraEventType.WHEEL,
          Cesium.CameraEventType.PINCH,
        ];
        ssc.lookEventTypes = [Cesium.CameraEventType.RIGHT_DRAG];
        ssc.rotateEventTypes = [];
        ssc.tiltEventTypes = [];

        // 관성 제거(테스트 시 편의)
        ssc.inertiaSpin = 0;
        ssc.inertiaTranslate = 0;
        ssc.inertiaZoom = 0;

        // Camera collision and zoom floor: avoid "sticking" to ground
        try {
          (ssc as any).enableCollisionDetection = false;
        } catch {}
        try {
          ssc.minimumZoomDistance = Math.max(
            0.5,
            ssc.minimumZoomDistance ?? 0.5,
          );
        } catch {}
      };
      applyControls();
      requestAnimationFrame(applyControls);
    } catch {
      //
    }

    // Block browser-level dblclick default on the canvas just in case
    const canvasEl: HTMLCanvasElement = v.scene.canvas;
    const onDblClick = (e: MouseEvent) => {
      e.preventDefault();
    };
    canvasEl?.addEventListener('dblclick', onDblClick);

    v.scene.camera.constrainedAxis = Cesium.Cartesian3.UNIT_Z;

    const scene = v.scene,
      globe = scene.globe;
    const original = {
      resolutionScale: v.resolutionScale ?? 1,
      fxaa: scene.postProcessStages.fxaa.enabled,
      mse: globe.maximumScreenSpaceError,
    };
    const onMoveStart = () => {
      v.resolutionScale = 0.7;
      globe.maximumScreenSpaceError = Math.max(12, original.mse);
      scene.requestRender();
    };
    const onMoveEnd = () => {
      v.resolutionScale = original.resolutionScale;
      globe.maximumScreenSpaceError = original.mse;
      scene.requestRender();
    };

    v.camera.moveStart.addEventListener(onMoveStart);
    v.camera.moveEnd.addEventListener(onMoveEnd);

    const clampAngles = () => {
      const c = v.scene.camera;
      // Allow near-top-down views
      const MIN_PITCH = Cesium.Math.toRadians(-90.0);
      const MAX_PITCH = Cesium.Math.toRadians(-1);
      const p = c.pitch;
      if (p < MIN_PITCH || p > MAX_PITCH) {
        c.setView({
          orientation: {
            heading: c.heading,
            pitch: Math.min(Math.max(p, MIN_PITCH), MAX_PITCH),
            // Preserve current roll; do not force 0
            roll: c.roll,
          },
        });
      }
    };
    scene.postRender.addEventListener(clampAngles);
    scene.requestRender();

    return () => {
      v.camera.moveStart.removeEventListener(onMoveStart);
      v.camera.moveEnd.removeEventListener(onMoveEnd);
      scene.postRender.removeEventListener(clampAngles);
      canvasEl?.removeEventListener('dblclick', onDblClick);
    };
  }, [viewerRef]);

  useEffect(() => {
    ensureGlobalEnuByLonLat(0, 0, 0);
    const off = tune();
    return () => {
      off?.();
    };
  }, [tune]);

  return (
    <Viewer
      className="absolute inset-0"
      ref={viewerRef}
      terrainProvider={terrainProvider}
      infoBox={false}
      selectionIndicator={false}
      animation={false}
      timeline={false}
      baseLayerPicker={false}
      geocoder={false}
      homeButton={false}
      navigationHelpButton={false}
      sceneModePicker={false}
      fullscreenButton={false}
      requestRenderMode
      shouldAnimate
      shadows={false}
      msaaSamples={4}
    >
      {children}
    </Viewer>
  );
}
