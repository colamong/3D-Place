// src/pages/MapEditorPage.tsx
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as Cesium from 'cesium';
import { ScreenSpaceEventType, Cartesian2 } from 'cesium';
import { ScreenSpaceEventHandler, ScreenSpaceEvent } from 'resium';
import type { CesiumComponentRef } from 'resium';
import type { Viewer as CesiumViewer } from 'cesium';

import CesiumCanvas from '../components/main/CesiumCanvas';
import RightDock from '../components/main/RightDock';
import VoxelLayer from '@/components/main/VoxelLayer';
import GhostLayer from '@/components/main/GhostLayer';
import SelectedOverlay from '@/components/main/SelectedOverlay';
import ColorToast from '@/components/main/ColorToast';
import VoxelControls from '@/components/main/VoxelControls';
import MeterGridLines from '@/components/main/MeterGridLines';
import ChunkModelLayer from '@/components/main/ChunkModelLayer';
import LoginToastModal from '@/components/common/LoginToastModal';

import {
  OVERLAY_IDS,
  BASE_LIFT,
  PAINT_MAX_CHARGES,
  ZOOM,
} from '@/features/voxels/constants';
import { useVoxelPicking } from '@/features/voxels/hooks/useVoxelPicking';
import { useVoxelStateStore } from '@/stores/useVoxelStateStore';
import { useVoxelWorld } from '@/features/voxels/hooks/useVoxelWorld';
import { keysFromCenter } from '@/features/voxels/chunk';
import {
  CHUNK_N,
  GRID_ENABLE_PX,
  ERASER_MAX_CHARGES,
} from '@/features/voxels/constants';
import { rgbToBase64 } from '@/utils/voxelPayload';
import { postPaints, deletePaints, type ServerPaintEvent } from '@/api/paints';
import { newOpId } from '@/utils/id';
import { useVoxelSeqStore } from '@/stores/useVoxelSeqStore';
import { useVoxelOpStore } from '@/stores/useVoxelOpStore';
import { useViewerBasics } from '@/features/voxels/hooks/useViewerBasics';
import { useGridAutoToggle } from '@/features/voxels/hooks/useGridAutoToggle';
import { useOverlayStore } from '@/stores/useOverlayStore';
import ModeSelectorPanel, {
  type Mode,
} from '@/components/main/ModeSelectorPanel';
import type { Voxel } from '@/features/voxels/types';

import { useVoxelSocketEvents } from '@/features/voxels/hooks/useVoxelSocket';

import { useChunkLoader } from '@/features/voxels/hooks/useChunkLoader';
import { useAuthStateQuery } from '@/features/auth/queries/query';
import { useToast } from '@/components/common/ToastProvider';

export default function MapEditorPage() {
  const { mapOn, toggleMap } = useOverlayStore();
  const { showToast } = useToast();
  const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null);
  const { getTileBaseHeight, getViewer } = useViewerBasics(
    viewerRef,
    BASE_LIFT,
  );
  const stableGetTileBaseHeight = useCallback(getTileBaseHeight, []);

  const pushVoxelsBatch = useVoxelStateStore((s) => s.pushVoxelsBatch);
  const updateVoxelColor = useVoxelStateStore((s) => s.updateVoxelColor);
  const eraseVoxelById = useVoxelStateStore((s) => s.eraseVoxelById);

  const wsUrl = import.meta.env.VITE_VOXEL_WS_URL as string | undefined;

  useVoxelSocketEvents(
    wsUrl,
    { pushVoxelsBatch, updateVoxelColor, eraseVoxelById },
    {
      getTileBaseHeight: stableGetTileBaseHeight,
      ZOOM,
      batchMs: 200,
    },
  );

  const [viewer, setViewer] = useState<CesiumViewer | null>(null);

  useEffect(() => {
    const v = viewerRef.current?.cesiumElement;
    if (v && v !== viewer) {
      console.log('✅ Viewer 준비 완료!');
      setViewer(v);
    }
  }, [viewerRef.current?.cesiumElement]);

  useChunkLoader(viewer, getTileBaseHeight, true);

  const camInitRef = useRef<null | {
    heading: number;
    pitch: number;
    roll: number;
    lon: number;
    lat: number;
    height: number;
  }>(null);

  function logCam(label: string) {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const cam = v.camera;
    const cpos = cam.positionCartographic;
    const toDeg = (rad: number) =>
      Number(Cesium.Math.toDegrees(rad).toFixed(2));
    const heading = toDeg(cam.heading);
    const pitch = toDeg(cam.pitch);
    const roll = toDeg(cam.roll);
    const lon = toDeg(cpos.longitude);
    const lat = toDeg(cpos.latitude);
    const height = Number(cpos.height.toFixed(2));

    if (!camInitRef.current) {
      camInitRef.current = { heading, pitch, roll, lon, lat, height };
    }

    const init = camInitRef.current;
    console.log(`[${label}]`, { heading, pitch, roll, lon, lat, height }, '△', {
      dHeading: Number((heading - init.heading).toFixed(2)),
      dPitch: Number((pitch - init.pitch).toFixed(2)),
      dRoll: Number((roll - init.roll).toFixed(2)),
      dLon: Number((lon - init.lon).toFixed(6)),
      dLat: Number((lat - init.lat).toFixed(6)),
      dH: Number((height - init.height).toFixed(2)),
    });
  }

  const [toolMode, setToolMode] = useState<Mode>('paint');
  const [overlaySpawnExpanded, setOverlaySpawnExpanded] = useState(false);
  const [panelPlacement, setPanelPlacement] = useState<'overlay' | 'dock'>(
    () => {
      try {
        const v = localStorage.getItem('ui:modePanelPlacement');
        return v === 'dock' ? 'dock' : 'overlay';
      } catch {
        return 'overlay';
      }
    },
  );

  useEffect(() => {
    try {
      localStorage.setItem('ui:modePanelPlacement', panelPlacement);
    } catch {
      /* noop */
    }
  }, [panelPlacement]);

  useEffect(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const onChanged = () => logCam('camera.changed');
    try {
      v.camera.changed.addEventListener(onChanged);
    } catch {
      /* noop */
    }
    return () => {
      try {
        v.camera.changed.removeEventListener(onChanged);
      } catch {
        /* noop */
      }
    };
  }, [viewerRef]);

  const initialCamRef = useRef<{
    heading: number;
    pitch: number;
    roll: number;
  } | null>(null);
  useEffect(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v || initialCamRef.current) return;
    const cam = v.camera;
    const saveOnce = () => {
      if (!initialCamRef.current) {
        initialCamRef.current = {
          heading: cam.heading,
          pitch: cam.pitch,
          roll: cam.roll,
        };
      }
      try {
        cam.changed.removeEventListener(saveOnce);
      } catch {
        /* noop */
      }
    };
    try {
      cam.changed.addEventListener(saveOnce);
    } catch {
      /* noop */
    }
    requestAnimationFrame(() => {
      if (!initialCamRef.current) {
        initialCamRef.current = {
          heading: cam.heading,
          pitch: cam.pitch,
          roll: cam.roll,
        };
      }
    });
  }, []);

  const terrainProvider = useMemo<Cesium.TerrainProvider | undefined>(() => {
    try {
      const cwt = (
        Cesium as unknown as {
          createWorldTerrain?: () => Cesium.TerrainProvider;
        }
      ).createWorldTerrain;
      return typeof cwt === 'function' ? cwt() : undefined;
    } catch {
      return undefined;
    }
  }, []);

  const ZOOM_RATIO = 0.3;
  const DURATION = 0.45;
  const MIN_H = 50.0,
    MAX_H = 4_000_000;

  const smoothZoomTo = useCallback((targetH: number) => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const cam = v.camera;
    const c = cam.positionCartographic;
    const h = Math.min(Math.max(targetH, MIN_H), MAX_H);
    const camAny = cam as unknown as { cancelFlight?: () => void };
    camAny.cancelFlight?.();
    cam.flyTo({
      destination: Cesium.Cartesian3.fromRadians(c.longitude, c.latitude, h),
      orientation: { heading: cam.heading, pitch: cam.pitch, roll: cam.roll },
      duration: DURATION,
      maximumHeight: Math.max(c.height, h),
      complete: () => v.scene.requestRender(),
      cancel: () => v.scene.requestRender(),
    });
  }, []);

  const onZoomIn = useCallback(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const curH = v.camera.positionCartographic.height || 1;
    smoothZoomTo(curH * (1 - ZOOM_RATIO));
  }, [smoothZoomTo]);

  const multiSelectRef = useRef(false);

  const onZoomOut = useCallback(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const curH = v.camera.positionCartographic.height || 1;
    smoothZoomTo(curH * (1 + ZOOM_RATIO));
  }, [smoothZoomTo]);

  const {
    drafts,
    commitSomeDrafts,
    clearDrafts,
    r,
    g,
    b,
    setR,
    setG,
    setB,
    showGrid,
    setShowGrid,
    gridWidth,
    setGridWidth,
    eraseMode,
    setEraseMode,
    eraserCharges,
    paintCharges,
    handleMove,
    handleClick,
    handleRightClick,
    unlockCamera,
    isColorToastOpen,
    closeColorToast,
    setPaintCharges,
    commitSelectedColors,
    commitEraseSelected,
    selectedVoxelIds,
  } = useVoxelWorld(
    viewerRef,
    toolMode,
    (m: 'paint' | 'erase' | 'eyedropper' | 'select' | 'move' | 'rotate') =>
      setToolMode((m === 'move' || m === 'rotate' ? 'paint' : m) as Mode),
    multiSelectRef,
  );

  const { data: authState } = useAuthStateQuery();
  const isAuthenticated = authState?.authenticated === true;
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const openLoginPrompt = useCallback(() => {
    try {
      closeColorToast();
    } catch {
      /* noop */
    }
    clearDrafts();
    setLoginPromptOpen(true);
  }, [closeColorToast, clearDrafts]);
  const ensureLoggedIn = useCallback(() => {
    if (isAuthenticated) return true;
    openLoginPrompt();
    return false;
  }, [isAuthenticated, openLoginPrompt]);
  const redirectToLogin = useCallback(() => {
    if (typeof window === 'undefined') return;
    const gw = (import.meta.env.VITE_GATEWAY_URL ?? '').replace(/\/$/, '');
    const base = gw ? `${gw}/api/login` : '/api/login';
    const next = `${window.location.pathname}${window.location.search}`;
    const url = `${base}?next=${encodeURIComponent(next)}`;
    window.location.assign(url);
  }, []);

  const storeVoxels = useVoxelStateStore((s) => s.voxels);
  const { pickVoxelEntity } = useVoxelPicking(
    getViewer,
    storeVoxels,
    OVERLAY_IDS,
  );

  const moveRef = useRef(handleMove);
  const clickRef = useRef(handleClick);
  const rightClickRef = useRef(handleRightClick);
  useEffect(() => {
    moveRef.current = handleMove;
  }, [handleMove]);
  useEffect(() => {
    clickRef.current = handleClick;
  }, [handleClick]);
  useEffect(() => {
    rightClickRef.current = handleRightClick;
  }, [handleRightClick]);

  type EvtObj = { position?: Cartesian2; endPosition?: Cartesian2 };
  const onMoveStable = useCallback((e: EvtObj) => moveRef.current?.(e), []);
  const onRightClickStable = useCallback(
    (p: Cartesian2 | undefined) => rightClickRef.current?.(p),
    [],
  );

  useEffect(() => {
    const onKeyDown = (ev: KeyboardEvent) => {
      if (ev.shiftKey || ev.ctrlKey || ev.metaKey)
        multiSelectRef.current = true;
    };
    const onKeyUp = (ev: KeyboardEvent) => {
      if (!ev.shiftKey && !ev.ctrlKey && !ev.metaKey)
        multiSelectRef.current = false;
    };
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
    };
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      const tag = (t?.tagName || '').toLowerCase();
      const typing =
        tag === 'input' ||
        tag === 'textarea' ||
        tag === 'select' ||
        (t?.isContentEditable ?? false);
      if (typing || e.ctrlKey || e.metaKey || e.altKey) return;
      if (isColorToastOpen) return;

      const k = e.key.toLowerCase();
      if (k === 't') {
        if (panelPlacement === 'dock') {
          setPanelPlacement('overlay');
          setOverlaySpawnExpanded(true);
          setTimeout(() => setOverlaySpawnExpanded(false), 0);
        } else {
          setPanelPlacement('dock');
        }
        e.preventDefault();
      } else if (k === 'o') {
        if (panelPlacement !== 'overlay') {
          setPanelPlacement('overlay');
          setOverlaySpawnExpanded(true);
          setTimeout(() => setOverlaySpawnExpanded(false), 0);
          e.preventDefault();
        }
      } else if (k === 'd') {
        if (panelPlacement !== 'dock') {
          setPanelPlacement('dock');
          e.preventDefault();
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [panelPlacement, isColorToastOpen]);

  const gridDisabled = useGridAutoToggle(viewerRef, showGrid, setShowGrid);

  const handlePaintAll = async () => {
    if (!ensureLoggedIn()) {
      return;
    }
    const pool = Math.max(0, paintCharges ?? 0);
    let used = 0;

    const draftCommitCount = Math.min(pool, drafts.length);
    let committedDrafts: Voxel[] = [];
    if (draftCommitCount > 0) {
      committedDrafts = (commitSomeDrafts?.(draftCommitCount) ?? []) as Voxel[];
      used += committedDrafts.length;
    }

    let changedIds: { id: string }[] = [];
    const selectable = selectedVoxelIds?.length ?? 0;
    const remain = Math.max(0, pool - used);
    const changeCount = Math.min(remain, selectable);
    if (changeCount > 0 && typeof commitSelectedColors === 'function') {
      changedIds = commitSelectedColors(changeCount, { r, g, b });
      used += changeCount;
    }

    if (used > 0 && setPaintCharges) {
      setPaintCharges((c: number) => Math.max(0, c - used));
    }

    if (draftCommitCount === 0 && changeCount > 0) {
      try {
        closeColorToast();
      } catch {
        /* noop */
      }
    }

    const changedVoxels: Voxel[] = changedIds
      .map(({ id }) => storeVoxels[id])
      .filter((v): v is Voxel => !!v);

    const seqGetNext = useVoxelSeqStore.getState().getNext;
    const seqSet = useVoxelSeqStore.getState().setFromServer;
    const getLastOp = useVoxelOpStore.getState().getLast;
    const setLastOp = useVoxelOpStore.getState().setLast;

    const toEvent = (v: Voxel, isColorChange = false): ServerPaintEvent => {
      const args = {
        z: v.z,
        tx: v.tx,
        ty: v.ty,
        cix: v.cx,
        ciy: v.cy,
        ciz: v.cz,
        vix: v.vx,
        viy: v.vy,
        viz: v.vz,
      };

      const existing = isColorChange
        ? (v.opId ?? getLastOp(args) ?? null)
        : null;

      const opId = newOpId();

      // ✅ vSeq 계산 수정
      const vSeq = isColorChange
        ? v.vSeq
          ? v.vSeq + 1
          : seqGetNext(args)
        : seqGetNext(args);

      return {
        opId,
        existingOpId: existing,
        vSeq,
        voxelIndex: { vix: v.vx, viy: v.vy, viz: v.vz },
        chunkIndex: {
          worldName: 'exampleWorld',
          tx: v.tx,
          ty: v.ty,
          cix: v.cx,
          ciy: v.cy,
          ciz: v.cz,
        },
        faceMask: 63,
        colorSchema: 'RGB1',
        colorBytes: rgbToBase64(v.r, v.g, v.b),
        timestamp: new Date().toISOString(),
        operationType: 'UPSERT',
      };
    };

    const events: ServerPaintEvent[] = [
      ...committedDrafts.map((v) => toEvent(v, false)),
      ...changedVoxels.map((v) => ({
        ...toEvent(v, true),
        colorBytes: rgbToBase64(r, g, b),
      })),
    ];

    try {
      if (events.length) {
        console.log('[HTTP] POST /paints events:', events.length);
        await postPaints(events);
      }
      for (const e of events) {
        const a = {
          z: ZOOM,
          tx: e.chunkIndex.tx,
          ty: e.chunkIndex.ty,
          cix: e.chunkIndex.cix,
          ciy: e.chunkIndex.ciy,
          ciz: e.chunkIndex.ciz,
          vix: e.voxelIndex.vix,
          viy: e.voxelIndex.viy,
          viz: e.voxelIndex.viz,
        };
        seqSet(a, e.vSeq);
        setLastOp(a, e.opId);
      }
    } catch (err) {
      console.warn('[paint] POST /paints failed', err);
    }
    try {
      viewerRef.current?.cesiumElement?.scene?.requestRender();
    } catch {
      /* noop */
    }
  };

  const onToggleGrid = useCallback(() => {
    if (gridDisabled) return;
    setShowGrid((v) => !v);
  }, [setShowGrid, gridDisabled]);

  const resetView = useCallback(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const cam = v.camera;
    const pos = cam.position;
    const heading = Cesium.Math.toRadians(360);
    const pitch = Cesium.Math.toRadians(-90.0);
    const roll = cam.roll;
    cam.setView({ destination: pos, orientation: { heading, pitch, roll } });
    v.scene.requestRender();
  }, []);

  const onClickEnhanced = useCallback(
    (e: EvtObj) => {
      const pos2 = e?.position ?? e?.endPosition;
      if (!pos2) return;
      try {
        pickVoxelEntity(pos2);
        clickRef.current?.(e);
      } catch (err) {
        console.warn('[onClickEnhanced] click failed:', err);
      }
    },
    [pickVoxelEntity],
  );

  const [myAlt] = useState(120);
  const flyToMyLocation = useCallback(
    (altitude = myAlt) => {
      const v = viewerRef.current?.cesiumElement;
      if (!v) return;
      if (!('geolocation' in navigator)) {
        console.warn('geolocation unsupported');
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const { latitude, longitude } = pos.coords;
          const orientTarget = {
            heading: Cesium.Math.toRadians(360),
            pitch: Cesium.Math.toRadians(-90.0),
            roll: v.camera.roll,
          };
          v.camera.flyTo({
            destination: Cesium.Cartesian3.fromDegrees(
              longitude,
              latitude,
              altitude,
            ),
            orientation: orientTarget,
            duration: 0.8,
            complete: () => {
              try {
                const scene = v.scene;
                const dist = (a?: Cesium.Cartesian2, b?: Cesium.Cartesian2) =>
                  !a || !b ? 0 : Math.hypot(a.x - b.x, a.y - b.y);
                const pxPerM = () => {
                  try {
                    const cpos = v.camera.positionCartographic;
                    const centerECEF = Cesium.Cartesian3.fromRadians(
                      cpos.longitude,
                      cpos.latitude,
                      0,
                    );
                    const enu =
                      Cesium.Transforms.eastNorthUpToFixedFrame(centerECEF);
                    const toWorld = (x: number, y: number) =>
                      Cesium.Matrix4.multiplyByPoint(
                        enu,
                        Cesium.Cartesian3.fromElements(
                          x,
                          y,
                          0.5,
                          new Cesium.Cartesian3(),
                        ),
                        new Cesium.Cartesian3(),
                      );
                    const p0 = Cesium.SceneTransforms.worldToWindowCoordinates(
                      scene,
                      toWorld(0, 0),
                    );
                    const p1 = Cesium.SceneTransforms.worldToWindowCoordinates(
                      scene,
                      toWorld(1, 0),
                    );
                    const p2 = Cesium.SceneTransforms.worldToWindowCoordinates(
                      scene,
                      toWorld(0, 1),
                    );
                    logCam('flyTo.complete');
                    return Math.max(dist(p0, p1), dist(p0, p2)) || 0;
                  } catch {
                    return 0;
                  }
                };
                const adjust = (iters = 5) => {
                  let i = 0;
                  const step = () => {
                    if (i++ >= iters) return;
                    const px = pxPerM();
                    if (px <= 0) return;
                    if (px >= GRID_ENABLE_PX) return;
                    const c = v.camera.positionCartographic;
                    const h = c.height;
                    const factor = Math.max(
                      0.3,
                      Math.min(0.9, px / GRID_ENABLE_PX),
                    );
                    const nextH = Math.max(1, h * factor);
                    v.camera.flyTo({
                      destination: Cesium.Cartesian3.fromRadians(
                        c.longitude,
                        c.latitude,
                        nextH,
                      ),
                      orientation: orientTarget,
                      duration: 0.35,
                      complete: () => step(),
                    });
                  };
                  step();
                };
                setTimeout(() => adjust(4), 60);
              } catch {
                /* noop */
              }
            },
          });
        },
        (err) => console.warn('geolocation error', err),
        { enableHighAccuracy: true, timeout: 5000, maximumAge: 10000 },
      );
    },
    [myAlt],
  );

  return (
    <div className="relative w-screen h-screen">
      <div style={{ display: isColorToastOpen ? 'none' : 'block' }}>
        <ModeSelectorPanel
          id="mode-selector-voxel"
          value={eraseMode ? 'erase' : toolMode}
          onModeChange={(m) => {
            setToolMode(m);
            if (m === 'erase') setEraseMode(true);
            if (m === 'paint' || m === 'select' || m === 'eyedropper')
              setEraseMode(false);
          }}
          placement={panelPlacement}
          onPlacementChange={setPanelPlacement}
          defaultExpanded={overlaySpawnExpanded}
          hotkeysEnabled={!isColorToastOpen}
        />
      </div>
      <CesiumCanvas viewerRef={viewerRef} terrainProvider={terrainProvider}>
        {showGrid && (
          <MeterGridLines
            viewerRef={viewerRef}
            enabled={showGrid}
            width={gridWidth}
            maxLines={CHUNK_N}
            hideWhileMoving={false}
          />
        )}

        <RightDock
          onZoomIn={onZoomIn}
          onZoomOut={onZoomOut}
          onToggleGrid={onToggleGrid}
          gridOn={showGrid}
          gridDisabled={gridDisabled}
          onToggleMap={toggleMap}
          mapOn={mapOn}
          onResetView={resetView}
          onFlyToMyLocation={() => flyToMyLocation()}
          modeDock={
            panelPlacement === 'dock'
              ? {
                  value:
                    toolMode === 'paint' ||
                    toolMode === 'eyedropper' ||
                    toolMode === 'select'
                      ? toolMode
                      : 'paint',
                  onChange: (m) => {
                    setToolMode(m as Mode);
                    setEraseMode(false);
                  },
                }
              : undefined
          }
          onModePanelPlacementChange={setPanelPlacement}
          onDockOpenOverlay={() => {
            setPanelPlacement('overlay');
            setOverlaySpawnExpanded(true);
            setTimeout(() => setOverlaySpawnExpanded(false), 0);
          }}
        />

        <ScreenSpaceEventHandler>
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.LEFT_CLICK}
            action={onClickEnhanced}
          />
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.LEFT_DOUBLE_CLICK}
            action={() => {
              /* consume */
            }}
          />
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.MOUSE_MOVE}
            action={onMoveStable}
          />
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.RIGHT_CLICK}
            action={(e) => {
              const ev = e as unknown as {
                position?: Cartesian2;
                endPosition?: Cartesian2;
              };
              onRightClickStable(ev.position ?? ev.endPosition);
            }}
          />
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.LEFT_DOWN}
            action={unlockCamera}
          />
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.MIDDLE_DOWN}
            action={unlockCamera}
          />
          <ScreenSpaceEvent
            type={ScreenSpaceEventType.RIGHT_DOWN}
            action={unlockCamera}
          />
        </ScreenSpaceEventHandler>

        <GhostLayer drafts={drafts} />
        <VoxelLayer voxels={storeVoxels} mapOn={mapOn} />
        {/* LOD1+ 렌더 전용 모델 레이어 */}
        <ChunkModelLayer getTileBaseHeight={stableGetTileBaseHeight} />
        {toolMode === 'select' && selectedVoxelIds?.length > 0 && (
          <SelectedOverlay
            voxels={storeVoxels}
            selectedIds={selectedVoxelIds}
          />
        )}
      </CesiumCanvas>

      <ColorToast
        visible={isColorToastOpen}
        onClose={() => {
          clearDrafts();
          closeColorToast();
        }}
        anchor="bc"
        showHeaderClose={false}
        width={Math.min(
          356,
          Math.max(360, Math.round(window.innerWidth * 0.34)),
        )}
      >
        <VoxelControls
          embedded
          r={r}
          g={g}
          b={b}
          setR={setR}
          setG={setG}
          setB={setB}
          showGrid={showGrid}
          setShowGrid={setShowGrid}
          gridWidth={gridWidth}
          setGridWidth={setGridWidth}
          onUnlock={unlockCamera}
          onClose={closeColorToast}
        />
        <div
          style={{
            marginTop: 8,
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          {(() => {
            const isSelect = toolMode === 'select';
            const countName = isSelect ? 'Selected' : 'Drafts';
            const selCount = isSelect
              ? (selectedVoxelIds?.length ?? 0)
              : drafts.length;
            return (
              <>
                <span>
                  {countName} {selCount}
                  {' / '}
                  {paintCharges ?? 0}
                </span>
                <span>
                  Paint {paintCharges ?? 0}/{PAINT_MAX_CHARGES}
                </span>
              </>
            );
          })()}
          <div>
            <button
              onClick={handlePaintAll}
              style={btnLight({
                bg: (paintCharges ?? 0) > 0 ? undefined : '#f1f5f9',
              })}
              disabled={(paintCharges ?? 0) <= 0}
              title={
                (paintCharges ?? 0) <= 0
                  ? 'No paint charges'
                  : 'Apply drafts + selection'
              }
            >
              Paint All
            </button>
            <button
              onClick={async () => {
                if (!ensureLoggedIn()) {
                  return;
                }
                try {
                  const sel = (selectedVoxelIds ?? [])
                    .map((id) => storeVoxels[id])
                    .filter(Boolean) as Voxel[];
                  const seqGetNext = useVoxelSeqStore.getState().getNext;
                  const seqSet = useVoxelSeqStore.getState().setFromServer;
                  const getLastOp = useVoxelOpStore.getState().getLast;
                  const setLastOp = useVoxelOpStore.getState().setLast;

                  const events: ServerPaintEvent[] = sel
                    .map((v) => {
                      // Derive precise indices from center for consistency
                      const k = keysFromCenter(
                        v.center,
                        stableGetTileBaseHeight,
                        ZOOM,
                      );
                      const args = {
                        z: k.z,
                        tx: k.tx,
                        ty: k.ty,
                        cix: k.cx,
                        ciy: k.cy,
                        ciz: k.ck,
                        vix: k.lx,
                        viy: k.ly,
                        viz: k.lk,
                      };

                      const existing = v.opId ?? getLastOp(args) ?? null;
                      if (!existing) {
                        console.warn(
                          '[erase] skip: missing existingOpId for',
                          args,
                        );
                        return null as any;
                      }
                      const opId = newOpId();
                      const vSeq = v.vSeq ? v.vSeq + 1 : seqGetNext(args);

                      return {
                        opId,
                        existingOpId: existing,
                        vSeq,
                        voxelIndex: { vix: k.lx, viy: k.ly, viz: k.lk },
                        chunkIndex: {
                          worldName: 'exampleWorld',
                          tx: k.tx,
                          ty: k.ty,
                          cix: k.cx,
                          ciy: k.cy,
                          ciz: k.ck,
                        },
                        timestamp: new Date().toISOString(),
                        operationType: 'ERASE',
                      } as ServerPaintEvent;
                    })
                    .filter(Boolean as any);
                  if (events.length) {
                    console.log(
                      '[HTTP] DELETE /paints/erase events:',
                      events.length,
                    );
                    await deletePaints(events);
                    for (const e of events) {
                      const a = {
                        z: ZOOM,
                        tx: e.chunkIndex.tx,
                        ty: e.chunkIndex.ty,
                        cix: e.chunkIndex.cix,
                        ciy: e.chunkIndex.ciy,
                        ciz: e.chunkIndex.ciz,
                        vix: e.voxelIndex.vix,
                        viy: e.voxelIndex.viy,
                        viz: e.voxelIndex.viz,
                      };
                      // After erase, reset local seq to 0 and clear last op mapping
                      seqSet(a, 0);
                      try {
                        useVoxelOpStore.getState().clear(a);
                      } catch {
                        /* noop */
                      }
                    }
                    commitEraseSelected();
                    closeColorToast();
                  }
                } catch (err) {
                  console.error('[erase] DELETE failed:', err);
                  showToast(getEraseErrorMessage(err), { variant: 'error' });
                }
              }}
              style={btnLight()}
              disabled={
                toolMode !== 'select' ||
                (eraserCharges ?? 0) <= 0 ||
                (selectedVoxelIds?.length ?? 0) <= 0
              }
              title={`Erase selected (${selectedVoxelIds?.length ?? 0}) · Charges ${eraserCharges ?? 0}/${ERASER_MAX_CHARGES}`}
            >
              Erase Selected
            </button>
          </div>
        </div>
      </ColorToast>
      <LoginToastModal
        visible={loginPromptOpen}
        onClose={() => setLoginPromptOpen(false)}
        onLogin={redirectToLogin}
      />
    </div>
  );
}

function btnLight(opt: { bg?: string } = {}) {
  return {
    padding: '6px 10px',
    borderRadius: 8,
    background: opt.bg ?? '#f8fafc',
    color: '#111',
    border: '1px solid rgba(0,0,0,.12)',
    cursor: 'pointer',
  } as const;
}

function getEraseErrorMessage(error: unknown): string {
  const payload = (
    error as {
      response?: {
        data?: {
          code?: string;
          message?: string;
        };
      };
    }
  )?.response?.data;

  if (
    payload &&
    typeof payload.code === 'string' &&
    payload.code.startsWith('PAINT-') &&
    typeof payload.message === 'string' &&
    payload.message.trim()
  ) {
    return payload.message;
  }

  return '삭제 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.';
}
