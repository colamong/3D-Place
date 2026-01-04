// src/features/voxels/hooks/useVoxelWorld.ts
import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import { Cartesian2, Cartesian3 } from 'cesium';
import type { ViewerRef, DraftVoxel, Voxel } from '@/features/voxels/types';
import {
  BASE_LIFT, MAX_DRAFTS, OVERLAY_IDS, VOXEL_SIZE_M, CENTER_OFFSET_M,
  ERASER_MAX_CHARGES, ERASER_REGEN_INTERVAL_MS, ZOOM,
  PAINT_MAX_CHARGES, PAINT_REGEN_INTERVAL_MS
} from '@/features/voxels/constants';
import { useViewerBasics } from './useViewerBasics';
import { useVoxelPicking } from './useVoxelPicking';
import { useVoxelDrafts } from './useVoxelDrafts';
import { useHoverFace } from './useHoverFace';
import { useClickHandler } from './useClickHandler';
import { useEraser } from './useEraser';
import { usePainter } from './usePainter';
import { useTestApis } from './useTestApis';
import { snapWorldToVoxelCenter } from '@/features/voxels/utils/snap';
import {
  makeDraftOnFace as makeDraftOnFaceSvc,
  makeDraftNextToDraft as makeDraftNextToDraftSvc
} from '@/features/voxels/services/placement';
import { useVoxelStateStore } from '@/stores/useVoxelStateStore';
import { keysFromCenter } from '../chunk';

export function useVoxelWorld(
  viewerRef: ViewerRef,
  toolMode?: 'paint' | 'erase' | 'eyedropper' | 'select' | 'move' | 'rotate',
  requestModeChange?: (m: 'paint' | 'erase' | 'eyedropper' | 'select' | 'move' | 'rotate') => void,
  multiSelectRef?: React.MutableRefObject<boolean | undefined>,
) {
  // ===== UI 로컬 상태 =====
  const [r, _setR] = useState(255);
  const [g, _setG] = useState(110);
  const [b, _setB] = useState(110);
  const colorRefObj = useRef<{ r: number; g: number; b: number }>({ r: 255, g: 110, b: 110 });
  const setR = useCallback((nv: number) => { colorRefObj.current.r = nv; _setR(nv); }, []);
  const setG = useCallback((nv: number) => { colorRefObj.current.g = nv; _setG(nv); }, []);
  const setB = useCallback((nv: number) => { colorRefObj.current.b = nv; _setB(nv); }, []);
  const [showGrid, setShowGrid] = useState(false);
  const [gridWidth, setGridWidth] = useState(1);
  const [isColorToastOpen, setColorToastOpen] = useState(false);
  const openColorToast = () => setColorToastOpen(true);
  const colorRef = colorRefObj.current; // 고정 참조 객체(프로퍼티만 변함)

  // ===== 뷰어/타일 =====
  const { getViewer, getTileBaseHeight, unlockCamera, logPickAt, getViewBounds } =
    useViewerBasics(viewerRef, BASE_LIFT);

  // ===== 전역 스토어 (Zustand) =====
  // ─ 액션 이름이 프로젝트마다 조금 다를 수 있으니 any로 안전 맵핑
  const voxels = useVoxelStateStore((s: any) => s.voxels as Record<string, Voxel>);
  const pushVoxelStore = useVoxelStateStore((s: any) =>
    // TODO: 스토어 액션 이름에 맞춰 순서/대체명 확인
    (s.pushVoxel ?? s.commitVoxel ?? ((v: Voxel) => undefined)) as (v: Voxel) => void
  );
  const eraseVoxelById = useVoxelStateStore((s: any) =>
    (s.eraseVoxelById ?? s.removeVoxel ?? s.deleteVoxel ?? ((id: string) => undefined)) as (id: string) => void
  );

  const updateVoxelColor = useVoxelStateStore((s: any) =>
    ((s.updateVoxelColor ?? s.paintVoxel) ??
      ((id: string, rgb: { r: number; g: number; b: number }) => undefined)
    ) as (id: string, rgb: { r: number; g: number; b: number }) => void
  );

  const clearAllVoxels = useVoxelStateStore((s: any) =>
    (s.clearAllVoxels ?? s.clearByTag ?? s.reset ?? (() => undefined)) as () => void
  );

  // ===== 드래프트 중복 방지 KeySet (z/x/y/k) =====
  const voxelIndexKey = (z: number, tx: number, ty: number, cx: number, cy: number, cz: number, vx: number, vy: number, vz: number) => 
    `${z}/${tx}/${ty}/${cx}/${cy}/${cz}/${vx}/${vy}/${vz}`;
  const voxelKeySetRef = useRef<Set<string>>(new Set());
  
  voxelKeySetRef.current.clear?.();
  for (const v of Object.values(voxels) as Voxel[]) {
    voxelKeySetRef.current.add(voxelIndexKey(v.z, v.tx, v.ty, v.cx, v.cy, v.cz, v.vx, v.vy, v.vz));
  }

  const commitVoxelFromCenter = useCallback(
    (center: Cartesian3, color?: { r: number; g: number; b: number }) => {
      const col = color ?? colorRefObj.current;
      
      // ✅ keysFromCenter로 전체 인덱스 계산
      const idx = keysFromCenter(center, getTileBaseHeight, ZOOM);
      
      // ✅ 서버 규격 ID
      const id = `${ZOOM}/${idx.tx}/${idx.ty}/${idx.cx}/${idx.cy}/${idx.ck}/${idx.lx}/${idx.ly}/${idx.lk}`;
      
      const v: Voxel = {
        id,
        z: ZOOM,
        // ✅ 모든 좌표 포함
        tx: idx.tx,
        ty: idx.ty,
        cx: idx.cx,
        cy: idx.cy,
        cz: idx.ck,
        vx: idx.lx,
        vy: idx.ly,
        vz: idx.lk,
        // ✅ 하위 호환용 (나중에 제거 가능)
        x: idx.tx,
        y: idx.ty,
        k: idx.ck,
        center: center, // keysFromCenter가 center 반환 안 하면 원본 사용
        r: col.r,
        g: col.g,
        b: col.b,
      };

      pushVoxelStore(v);
      return v;
    },
    [getTileBaseHeight, pushVoxelStore],
  );

  const drafts = useVoxelDrafts({
    MAX_DRAFTS,
    getTileBaseHeight,
    openColorToast,
    commitVoxel: commitVoxelFromCenter,   // ← 시그니처 맞춤
    voxelKeySet: voxelKeySetRef,          // ← Ref 요구
    defaultColor: colorRef,
  });

  // ===== 피킹/호버 =====
  const picking = useVoxelPicking(getViewer, voxels, OVERLAY_IDS);
  const { hoverFace, handleMove } = useHoverFace({
    getViewer, voxels, VOXEL_SIZE_M, safePickWorld: picking.safePickWorld,
  });

  // ===== 소모 리소스(페인트/지우개) =====
  const eraser = useEraser({
    ERASER_MAX_CHARGES, ERASER_REGEN_INTERVAL_MS,
    getViewer, OVERLAY_IDS,
    removeDraftById: drafts.removeDraftById,
    eraseVoxelById,
  });
  const painter = usePainter({ PAINT_MAX_CHARGES, PAINT_REGEN_INTERVAL_MS });
  const actionCharges = painter.paintCharges;
  const setActionCharges = painter.setPaintCharges;
  const actionRegenLeftMs = painter.paintRegenLeftMs;

  // ===== 팔레트 닫힐 때 정리 =====
  const { setEraseMode } = eraser;
  const closeAndReset = useCallback(() => {
    setColorToastOpen(false);
    setEraseMode(false);
    setSelectedVoxelIds([]);
    if (toolMode === 'select') requestModeChange?.('paint');
  }, [setEraseMode, toolMode, requestModeChange]);

  // ===== 위치/색 전달용 deps =====
  const placeDeps = useMemo(() => ({
    getViewer,
    pickHit: picking.pickHit,
    getTileBaseHeight,
    pushDraft: drafts.pushDraft,
    ZOOM,
    VOXEL_SIZE_M,
    color: colorRef,
  }), [getViewer, picking.pickHit, getTileBaseHeight, drafts.pushDraft, colorRef]);

  // ===== Draft 생성기 =====
  const makeDraftOnGround = (p: Cartesian2) => {
    const world = picking.safePickWorld(p); if (!world) return;
    const { center, tx, ty, k } = snapWorldToVoxelCenter(world, { ZOOM, VOXEL_SIZE_M, CENTER_OFFSET_M, getTileBaseHeight });
    const { r: rr, g: gg, b: bb } = colorRefObj.current;
    drafts.pushDraft({ z: ZOOM, x: tx, y: ty, k, center, r: rr, g: gg, b: bb });
  };
  const makeDraftOnFace = useCallback(
    (pos2: Cartesian2) => makeDraftOnFaceSvc(pos2, picking.pickVoxelEntity, voxels, placeDeps),
    [picking.pickVoxelEntity, voxels, placeDeps],
  );
  const makeDraftNextToDraft = useCallback(
    (baseD: DraftVoxel, pos2: Cartesian2) => makeDraftNextToDraftSvc(baseD, pos2, placeDeps),
    [placeDeps],
  );

  // ===== 기본 클릭 핸들러(페인트/지우개/고스트 삭제 포함) =====
  const clickBase = useClickHandler({
    getViewer,
    eraseMode: eraser.eraseMode,
    eraserCharges: actionCharges,
    setEraserCharges: setActionCharges,
    drafts: drafts.drafts,
    pickVoxelEntity: picking.pickVoxelEntity,
    makeDraftOnGround, makeDraftOnFace, makeDraftNextToDraft,
    removeDraftById: drafts.removeDraftById,
    eraseVoxelById,
  });

  // ===== 선택 상태 =====
  const [selectedVoxelIds, setSelectedVoxelIds] = useState<string[]>([]);
  const setSelectedVoxelIdsExternal = useCallback((ids: string[]) => {
    setSelectedVoxelIds(ids ?? []);
  }, []);

  const pickId = useCallback((pos2: Cartesian2) => {
    const ent = picking.pickVoxelEntity(pos2);
    console.debug('[pickId] ent:', ent);
    if (typeof ent === 'string') return ent as string;
    const id = (ent as { id?: unknown })?.id;
    console.debug('[pickId] resolved id:', id, 'existsInVoxels:', typeof id === 'string' && !!voxels[id]);
    return typeof id === 'string' ? id : undefined;
  }, [picking, voxels]);

  const handleClick2 = useCallback((e: { position?: Cartesian2; endPosition?: Cartesian2 }) => {
    const pos2 = e?.position ?? e?.endPosition; if (!pos2) return;

    if (toolMode === 'eyedropper') {
      const id = pickId(pos2);
      if (id && voxels[id]) {
        const v = voxels[id];
        setR(v.r); setG(v.g); setB(v.b);
        requestModeChange?.('paint');
        setEraseMode(false);
        openColorToast();
      }
      return;
    }

    if (toolMode === 'select') {
      const id = pickId(pos2);
      if (id && voxels[id]) {
        setSelectedVoxelIds(prev => {
          // 이미 선택돼 있으면 해제
          if (prev.includes(id)) return prev.filter(x => x !== id);
          // 새로 추가
          if (prev.length >= 50) return prev; // 선택 제한 optional
          return [...prev, id];
        });
        const vsel = voxels[id];
        setR(vsel.r); setG(vsel.g); setB(vsel.b);
        openColorToast();
      }
      return;
    }


    return clickBase(pos2);
  }, [toolMode, pickId, voxels, setR, setG, setB, openColorToast, clickBase, multiSelectRef, requestModeChange, setEraseMode]);

  useEffect(() => {
    if (toolMode !== 'select' && selectedVoxelIds.length) setSelectedVoxelIds([]);
  }, [toolMode, selectedVoxelIds.length]);

  // ===== 우클릭(고스트 삭제/지우개) =====
  const { pickVoxelEntity } = picking;
  const { removeDraftById } = drafts;
  const { eraseMode } = eraser;

  const handleRightClick = useCallback((pos2: Cartesian2 | undefined) => {
    if (!pos2) return;
    const v = getViewer(); const scene = v?.scene; if (!scene) return;

    try {
      const picks = (scene.drillPick ? scene.drillPick(pos2) : []) as { id?: unknown }[];
      const ids = picks.map(p => {
        const raw = p?.id;
        if (typeof raw === 'string') return raw;
        if (raw && typeof (raw as { id?: unknown }).id === 'string') return (raw as { id?: unknown }).id as string;
        return undefined;
      }).filter((s): s is string => typeof s === 'string');
      const ghost = ids.find(s => s.startsWith('__ghost/'));
      if (ghost) { removeDraftById(ghost.replace('__ghost/', '')); return; }
    } catch (e) { /* noop */ }

    if (eraseMode && actionCharges > 0) {
      const ent = pickVoxelEntity(pos2);
      const voxelId = (() => {
        if (typeof ent === 'string') return ent as string;
        if (ent && typeof (ent as { id?: unknown }).id === 'string') return (ent as { id?: unknown }).id as string;
        return undefined;
      })();
      if (voxelId) {
        eraseVoxelById(voxelId);
        setActionCharges(c => Math.max(0, c - 1));
      }
    }
  }, [getViewer, pickVoxelEntity, removeDraftById, eraseMode, actionCharges, setActionCharges, eraseVoxelById]);

  // ===== 테스트 API =====
  const test = useTestApis({
    getTileBaseHeight,
    pushDraft: drafts.pushDraft,
    commitVoxel: commitVoxelFromCenter,
    colorRef,
    VOXEL_SIZE_M,
    CENTER_OFFSET_M,
  });

  // ===== 선택 커밋/삭제 =====
  const commitSelectedColors = useCallback((maxCount: number, rgb?: { r: number; g: number; b: number }) => {
    if (maxCount <= 0) return [] as { id: string }[];
    const take = Math.min(maxCount, selectedVoxelIds.length);
    const changed: { id: string }[] = [];
    const col = rgb ?? colorRefObj.current;
    const appliedIds: string[] = [];
    for (let i = 0; i < take; i++) {
      const id = selectedVoxelIds[i];
      if (voxels[id]) {
        updateVoxelColor(id, { r: col.r, g: col.g, b: col.b });
        changed.push({ id });
        appliedIds.push(id);
      }
    }
    if (appliedIds.length) {
      setSelectedVoxelIds(prev => prev.filter(id => !appliedIds.includes(id)));
    }
    try { getViewer()?.scene?.requestRender(); } catch (e) { /* noop */ }
    return changed;
  }, [selectedVoxelIds, voxels, updateVoxelColor, getViewer]);

  const commitEraseSelected = useCallback((maxCount?: number) => {
    const maxByCharge = Math.max(0, actionCharges);
    const limit = typeof maxCount === 'number' ? Math.max(0, Math.min(maxCount, maxByCharge)) : maxByCharge;
    const take = Math.min(limit, selectedVoxelIds.length);
    const removed: string[] = [];
    for (let i = 0; i < take; i++) {
      const id = selectedVoxelIds[i];
      if (id) {
        eraseVoxelById(id);
        removed.push(id);
      }
    }
    if (removed.length) {
      setSelectedVoxelIds(prev => prev.filter(id => !removed.includes(id)));
      setActionCharges((c) => Math.max(0, c - removed.length));
      try { getViewer()?.scene?.requestRender(); } catch (e) { /* noop */ }
    }
    return removed;
  }, [selectedVoxelIds, actionCharges, eraseVoxelById, setActionCharges, getViewer]);

  // ===== 반환 =====
  return {
    // state
    voxels,
    drafts: drafts.drafts,
    draftLimitHit: drafts.draftLimitHit,
    hoverFace,
    pushDraft: drafts.pushDraft,
    r, g, b, setR, setG, setB,
    showGrid, setShowGrid, gridWidth, setGridWidth,
    eraseMode: eraser.eraseMode,
    setEraseMode: eraser.setEraseMode,

    // charges
    eraserCharges: actionCharges,
    eraserRegenLeftMs: actionRegenLeftMs,
    paintCharges: actionCharges,
    paintRegenLeftMs: actionRegenLeftMs,

    // selection
    isColorToastOpen, openColorToast, closeColorToast: closeAndReset,
    selectedVoxelIds, setSelectedVoxelIdsExternal,
    commitSelectedColors,
    commitEraseSelected,

    // actions
    handleMove: (e: { position?: Cartesian2; endPosition?: Cartesian2 }) => { const p = e?.endPosition ?? e?.position; if (p) handleMove(p); },
    handleClick: handleClick2,
    handleRightClick,
    commitAllDrafts: () => { const r = drafts.commitAllDrafts(); closeAndReset(); return r; },
    commitSomeDrafts: (n: number) => { const r = drafts.commitSomeDrafts(n); closeAndReset(); return r; },
    clearDrafts: () => { drafts.clearDrafts(); closeAndReset(); },
    unlockCamera, logPickAt, getViewBounds,
    eraseAnyAt: eraser.eraseAnyAt,

    // store helpers
    clearAllVoxels,
    pushVoxel: commitVoxelFromCenter,   // 외부에서 center로 밀어넣고 싶을 때
    clearByTag: clearAllVoxels,

    // test hooks
    ...test,
    setPaintCharges: setActionCharges,
  };
}