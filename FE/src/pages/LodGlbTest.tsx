import { useEffect, useMemo, useRef, useState } from 'react';
import type { CesiumComponentRef } from 'resium';
import type { Viewer as CesiumViewer } from 'cesium';
import * as Cesium from 'cesium';
import { WebIO } from '@gltf-transform/core';

import CesiumCanvas from '@/components/main/CesiumCanvas';
import { Entity } from 'resium';
import ChunkModel from '@/components/main/ChunkModel';
import { tileBounds, lonLatToTile } from '@/features/voxels/geo';
import { indexFromCenter } from '@/features/voxels/chunk';
import { CHUNK_N, VOXEL_SIZE_M } from '@/features/voxels/constants';
import { calculateLODByHeight } from '@/features/voxels/utils/chunkLoader';
import { centerFromChunkIndices } from '@/features/voxels/chunk';
import { ZOOM } from '@/features/voxels/constants';

// TEMP DEV PAGE: GLB LOD quick test
// - Route: /_dev/lod (wired in App.tsx only in DEV)
// - Loads /public/glb/lod{n}.glb as a Cesium Model (no interaction)
// - Adds simple controls to switch LOD, scale, and fly camera

export default function LodGlbTest() {
  // Default to 1 so something visible appears even if voxel import skips
  const [lod, setLod] = useState(1);
  const [readyMap, setReadyMap] = useState<Record<string, boolean>>({});
  const [bsMap, setBsMap] = useState<Record<string, Cesium.BoundingSphere | undefined>>({});
  const [unitScale, setUnitScale] = useState(VOXEL_SIZE_M); // meters per GLB unit
  const [heightOffset, setHeightOffset] = useState(0); // meters above base cell
  const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null);
  const [hud, setHud] = useState<{
    lon: number; lat: number; h: number;
    heading: number; pitch: number; roll: number;
    groundH: number; hAboveGround: number;
    tx: number; ty: number; cx: number; cy: number; cz: number;
  } | null>(null);
  // 자동 LOD 전환 (기본 끔 — 수동으로 LOD 선택 가능)
  const [autoLod, setAutoLod] = useState(true);
  // GLB extents 기반 자동 오프셋(X,Y) 적용 토글 + 캐시
  const [autoOffset, setAutoOffset] = useState(true);
  const [lodOffset, setLodOffset] = useState<Record<number, { x: number; y: number } | undefined>>({});
  const [offsetLoading, setOffsetLoading] = useState(false);
  const [assetOk, setAssetOk] = useState<boolean | null>(null);

  // Place at Seoul tile per request
  const tx = 436;
  const ty = 198;
  // 유저가 LOD0에서 놓았다고 가정하는 "단일" 청크 앵커(예: 타일 내 0,0,0)
  const userChunkList = [
    { id: 'c000', cx: 0, cy: 0, ck: 0 },
  ] as const;

  // 이번 테스트는 "한 청크만" 렌더링 — LOD에 상관없이 동일 앵커 사용
  const getChunksForLod = (_level: number) => userChunkList as readonly {id:string;cx:number;cy:number;ck:number}[];

  // Importer ignores getTileBaseHeight in favor of flatBaseHeight param; still provide stub
  const getTileBaseHeight = useMemo(() => (z: number, _x: number, _y: number) => {
    void z; // unused
    return 0;
  }, []);

  const onClearCache = () => {
    // no-op now (kept button removed below)
  };

  const onFlyToTileCenter = () => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const { west, south, east, north } = tileBounds(tx, ty, ZOOM);
    const lon = (west + east) / 2;
    const lat = (south + north) / 2;
    v.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(lon, lat, 5000),
      orientation: { heading: v.camera.heading, pitch: v.camera.pitch, roll: v.camera.roll },
      duration: 0.6,
      maximumHeight: 100000,
    });
  };

  // Auto move camera to tile on first mount
  useEffect(() => {
    console.info('[LOD] init', { z: ZOOM, tx, ty });
    onFlyToTileCenter();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Check GLB asset existence for current LOD
  useEffect(() => {
    const url = `/glb/lod${lod}.glb`;
    setAssetOk(null);
    fetch(url, { method: 'HEAD' })
      .then((res) => {
        const ok = res.ok;
        setAssetOk(ok);
        if (!ok) console.warn('[GLB] missing', url, res.status);
      })
      .catch((e) => { setAssetOk(false); console.warn('[GLB] missing', url, e); });
  }, [lod]);

  // Auto compute local offset (x,y) from GLB POSITION extents (min -> 0)
  useEffect(() => {
    if (!autoOffset) return;
    if (lodOffset[lod]) return; // cached
    const url = `/glb/lod${lod}.glb`;
    setOffsetLoading(true);
    (async () => {
      try {
        const io = new WebIO();
        const doc = await io.read(url);
        const nodes = doc.getRoot().listNodes();
        let minX = Infinity, minY = Infinity;
        let any = false;
        for (const n of nodes) {
          const m = n.getMesh?.();
          if (!m) continue;
          const p = m.listPrimitives()[0];
          if (!p) continue;
          const pos = p.getAttribute('POSITION')?.getArray() as Float32Array | undefined;
          if (!pos) continue;
          // Node-level translation (TRS or matrix). Rotation/scale는 무시(정수칸 보정 목적)
          let tX = 0, tY = 0;
          try {
            const t = (n as any).getTranslation?.();
            if (t && (t[0] || t[1])) { tX += t[0] || 0; tY += t[1] || 0; }
            const mat = (n as any).getMatrix?.();
            if (mat && mat.length === 16) { tX += mat[12] || 0; tY += mat[13] || 0; }
          } catch {}
          for (let i = 0; i < pos.length; i += 3) {
            const x = pos[i] + tX, y = pos[i + 1] + tY;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
          }
          any = true;
        }
        if (!any || !isFinite(minX) || !isFinite(minY)) {
          console.warn('[GLB] autoOffset: no POSITION found');
          setOffsetLoading(false);
          return;
        }
        // Only correct FRACTIONAL offset w.r.t. cell grid (keep integer cell index as-is)
        const frac = (v: number) => {
          const f = ((v % 1) + 1) % 1; // [0,1)
          // Snap near 0 or 0.5 for stability
          if (Math.abs(f - 0) < 1e-3) return 0;
          if (Math.abs(f - 0.5) < 1e-3) return 0.5;
          return f;
        };
        const fx = frac(minX);
        const fy = frac(minY);
        // If minX/minY is very close to an integer N, also remove that integer-cell offset
        const nearInt = (v: number) => {
          const n = Math.round(v);
          return Math.abs(v - n) < 1e-3 ? n : 0;
        };
        const ix = nearInt(minX);
        const iy = nearInt(minY);
        // scale per LOD (2x for LOD2, 4x for LOD3)
        const unit = lod === 2 ? VOXEL_SIZE_M * 2 : lod === 3 ? VOXEL_SIZE_M * 4 : VOXEL_SIZE_M;
        const ox = -(ix + fx) * unit;
        const oy = -(iy + fy) * unit;
        setLodOffset((prev) => ({ ...prev, [lod]: { x: ox, y: oy } }));
        console.info('[GLB] autoOffset computed', { lod, minX, minY, ix, iy, fx, fy, unit, offset: { x: ox, y: oy } });
      } catch (e) {
        console.warn('[GLB] autoOffset failed', { url, e });
      } finally {
        setOffsetLoading(false);
      }
    })();
  }, [autoOffset, lod, VOXEL_SIZE_M]);

  // HUD updater: listen camera.changed and sample center ground, optional auto-LOD swap
  useEffect(() => {
    const v = viewerRef.current?.cesiumElement;
    if (!v) return;
    const { camera, scene } = v;

    const update = () => {
      try {
        const c = camera.positionCartographic;
        const lon = Cesium.Math.toDegrees(c.longitude);
        const lat = Cesium.Math.toDegrees(c.latitude);
        const h = c.height;
        const heading = Cesium.Math.toDegrees(camera.heading);
        const pitch = Cesium.Math.toDegrees(camera.pitch);
        const roll = Cesium.Math.toDegrees(camera.roll);

        // pick ground at screen center (가벼운 계산) + 인덱스 계산은 120ms 간격으로만 수행
        const center = new Cesium.Cartesian2(scene.canvas.clientWidth / 2, scene.canvas.clientHeight / 2);
        const onGlobe = camera.pickEllipsoid(center, scene.globe.ellipsoid);
        const groundH = onGlobe
          ? Cesium.Ellipsoid.WGS84.cartesianToCartographic(onGlobe).height
          : 0;
        const hAboveGround = Math.max(0, h - groundH);

        // 청크/타일 인덱스는 너무 자주 계산하지 않도록 스로틀
        const now = performance.now();
        (update as any)._lastIdxAt = (update as any)._lastIdxAt ?? 0;
        (update as any)._lastIdx = (update as any)._lastIdx ?? { tx: 0, ty: 0, cx: 0, cy: 0, cz: 0 };
        let txCur = (update as any)._lastIdx.tx;
        let tyCur = (update as any)._lastIdx.ty;
        let cxCur = (update as any)._lastIdx.cx;
        let cyCur = (update as any)._lastIdx.cy;
        let czCur = (update as any)._lastIdx.cz;
        if (now - (update as any)._lastIdxAt > 120) {
          const idx = indexFromCenter(camera.position, () => 0, ZOOM);
          txCur = idx.tx; tyCur = idx.ty;
          cxCur = Math.floor(idx.ix / CHUNK_N);
          cyCur = Math.floor(idx.iy / CHUNK_N);
          czCur = 0;
          (update as any)._lastIdx = { tx: txCur, ty: tyCur, cx: cxCur, cy: cyCur, cz: czCur };
          (update as any)._lastIdxAt = now;
        }

        setHud({ lon, lat, h, heading, pitch, roll, groundH, hAboveGround, tx: txCur, ty: tyCur, cx: cxCur, cy: cyCur, cz: czCur });

        // Auto LOD by altitude (옵션) — 기본 꺼짐
        if (autoLod) {
          const next = calculateLODByHeight(h);
          if (next !== lod) {
            console.info('[LOD] auto', lod, '→', next);
            setReadyMap({});
            setBsMap({});
            setLod(next);
          }
        }
      } catch {
        // ignore
      }
    };
    try { camera.changed.addEventListener(update); } catch {}
    update();
    return () => { try { camera.changed.removeEventListener(update); } catch {} };
  }, [viewerRef.current?.cesiumElement, lod, autoLod]);

  // Reset state when switching LOD
  useEffect(() => {
    setReadyMap({});
    setBsMap({});
  }, [lod]);

  const getAnchor = (cx: number, cy: number, ck: number) =>
    centerFromChunkIndices({
      tile: { x: tx, y: ty },
      chunk: { cx, cy, ck },
      local: { lx: 0, ly: 0, lk: 0 },
      getTileBaseHeight,
      zoom: ZOOM,
    });

  return (
    <div className="w-screen h-screen relative">
      {/* Controls */}
      <div className="absolute top-3 left-3 z-10 bg-white/85 backdrop-blur rounded shadow p-3 text-sm flex gap-2 items-center flex-wrap">
        <div className="flex items-center gap-2">
          <label htmlFor="lod-sel">LOD</label>
          <select
            id="lod-sel"
            value={lod}
            className="border rounded px-1 py-0.5"
            disabled
          >
            <option value={0}>0</option>
            <option value={1}>1</option>
            <option value={2}>2</option>
            <option value={3}>3</option>
          </select>
        </div>
        {lod >= 0 && (
          <div className="flex items-center gap-2">
            <label htmlFor="scale-sel">Scale</label>
            <select
              id="scale-sel"
              value={unitScale}
              onChange={(e) => setUnitScale(parseInt(e.target.value, 10))}
              className="border rounded px-1 py-0.5"
              disabled
            >
              <option value={VOXEL_SIZE_M}>{VOXEL_SIZE_M}</option>
            </select>
          </div>
        )}
        <button
          className="border rounded px-2 py-1"
          onClick={async () => {
            const url = `/glb/lod${lod}.glb`;
            try {
              const io = new WebIO();
              const doc = await io.read(url);
              const root = doc.getRoot();
              const nodes = root.listNodes();
              const meshes = root.listMeshes();
              const images = root.listTextures();
              const materials = root.listMaterials();

              const summary: any = {
                url,
                stats: {
                  nodes: nodes.length,
                  meshes: meshes.length,
                  materials: materials.length,
                  textures: images.length,
                },
                nodes: [] as any[],
              };

              let gmin = { x: Infinity, y: Infinity, z: Infinity };
              let gmax = { x: -Infinity, y: -Infinity, z: -Infinity };
              for (const node of nodes) {
                const name = node.getName?.() ?? '';
                const extras = node.getExtras?.();
                const mesh = node.getMesh?.();
                let primInfo: any = null;
                if (mesh) {
                  const prim = mesh.listPrimitives()[0];
                  if (prim) {
                    const pos = prim.getAttribute('POSITION')?.getArray() as Float32Array | undefined;
                    const col = prim.getAttribute('COLOR_0')?.getArray() as Float32Array | undefined;
                    if (pos) {
                      let minX = Infinity, maxX = -Infinity;
                      let minY = Infinity, maxY = -Infinity;
                      let minZ = Infinity, maxZ = -Infinity;
                      for (let i = 0; i < pos.length; i += 3) {
                        const x = pos[i], y = pos[i + 1], z = pos[i + 2];
                        if (x < minX) minX = x; if (x > maxX) maxX = x;
                        if (y < minY) minY = y; if (y > maxY) maxY = y;
                        if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                      }
                      gmin.x = Math.min(gmin.x, minX); gmax.x = Math.max(gmax.x, maxX);
                      gmin.y = Math.min(gmin.y, minY); gmax.y = Math.max(gmax.y, maxY);
                      gmin.z = Math.min(gmin.z, minZ); gmax.z = Math.max(gmax.z, maxZ);
                      primInfo = {
                        vertices: pos.length / 3,
                        bbox: { min: [minX, minY, minZ], max: [maxX, maxY, maxZ] },
                        hasColor: !!col,
                      };
                    }
                  }
                }
                summary.nodes.push({ name, hasMesh: !!mesh, extras, primitive: primInfo });
              }
              summary.extents = { min: gmin, max: gmax };

              console.groupCollapsed(`[GLB] Summary ${url}`);
              console.table(summary.stats);
              console.log('extents', summary.extents);
              console.log('nodes (first 20)', summary.nodes.slice(0, 20));
              console.groupEnd();

              const blob = new Blob([JSON.stringify(summary, null, 2)], { type: 'application/json' });
              const a = document.createElement('a');
              a.href = URL.createObjectURL(blob);
              a.download = `lod${lod}-summary.json`;
              a.click();
              URL.revokeObjectURL(a.href);
            } catch (e) {
              console.error('[GLB] dump failed', { url, error: e });
            }
          }}
        >Dump</button>
        {lod >= 0 && (
          <div className="flex items-center gap-2">
            <label htmlFor="height-sel">Alt(m)</label>
            <select
              id="height-sel"
              value={heightOffset}
              onChange={(e) => setHeightOffset(parseInt(e.target.value, 10))}
              className="border rounded px-1 py-0.5"
            >
              <option value={0}>0</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
              <option value={200}>200</option>
            </select>
          </div>
        )}
        <button className="border rounded px-2 py-1" onClick={onFlyToTileCenter}>Fly Tile</button>
        <button
          className="border rounded px-2 py-1"
          onClick={() => {
            try {
              const v = viewerRef.current?.cesiumElement; if (!v) return;
              const spheres: Cesium.BoundingSphere[] = [];
              for (const c of getChunksForLod(lod)) {
                const bs = bsMap[c.id]; if (bs) spheres.push(bs);
              }
              if (spheres.length > 0) {
                let acc = spheres[0];
                for (let i = 1; i < spheres.length; i++) acc = Cesium.BoundingSphere.union(acc, spheres[i], new Cesium.BoundingSphere());
                v.camera.flyToBoundingSphere(acc, { duration: 0.8 });
              } else {
                const base = getChunksForLod(lod)[0];
                const a = getAnchor(base.cx, base.cy, base.ck);
                const carto = Cesium.Ellipsoid.WGS84.cartesianToCartographic(a);
                const lon = Cesium.Math.toDegrees(carto.longitude);
                const lat = Cesium.Math.toDegrees(carto.latitude);
                v.camera.flyTo({ destination: Cesium.Cartesian3.fromDegrees(lon, lat, Math.max(500, carto.height + 3000)) });
              }
            } catch (e) {
              console.error('[LOD] Fly All error', e);
            }
          }}
        >Fly All</button>
        <div className="ml-2 text-gray-600">ready: {Object.values(readyMap).filter(Boolean).length}/{getChunksForLod(lod).length}</div>
        <div className="w-full" />
        {getChunksForLod(lod).map(c => (
          <button
            key={`fly-${c.id}`}
            className="border rounded px-2 py-1"
            onClick={() => {
              try {
                const v = viewerRef.current?.cesiumElement; if (!v) return;
                const a = getAnchor(c.cx, c.cy, c.ck);
                const carto = Cesium.Ellipsoid.WGS84.cartesianToCartographic(a);
                const lon = Cesium.Math.toDegrees(carto.longitude);
                const lat = Cesium.Math.toDegrees(carto.latitude);
                console.info('[LOD] Fly Anchor', { id: c.id, cx: c.cx, cy: c.cy, ck: c.ck, lon, lat, h: carto.height });
                v.camera.flyTo({ destination: Cesium.Cartesian3.fromDegrees(lon, lat, Math.max(500, carto.height + 3000)) });
              } catch (e) {
                console.error('[LOD] Fly Anchor error', e);
              }
            }}
          >Fly {c.id}</button>
        ))}
      </div>

      {/* HUD: camera + ground info */}
      <div className="absolute bottom-3 left-3 z-10 bg-white/85 backdrop-blur rounded shadow p-2 text-xs leading-5">
        {hud ? (
          <div>
            <div>lon {hud.lon.toFixed(6)}, lat {hud.lat.toFixed(6)}</div>
            <div>h {Math.round(hud.h)} m, ground {Math.round(hud.groundH)} m, AGL {Math.round(hud.hAboveGround)} m</div>
            <div>hd {hud.heading.toFixed(1)}°, pt {hud.pitch.toFixed(1)}°, rl {hud.roll.toFixed(1)}°</div>
            <div>tile tx {hud.tx}, ty {hud.ty} | chunk cx {hud.cx}, cy {hud.cy}, cz {hud.cz}</div>
            <div>asset {assetOk === null ? 'checking…' : assetOk ? 'OK' : 'MISSING'}</div>
          </div>
        ) : (
          <div>Loading HUD…</div>
        )}
        {/* Auto LOD is always ON in this test */}
        <label className="flex items-center gap-1 ml-2 select-none">
          <input type="checkbox" checked={autoOffset} onChange={(e) => setAutoOffset(e.target.checked)} />
          Auto Offset
        </label>
        {autoOffset && lodOffset[lod] && (
          <span className="text-gray-600"> offset: x {Math.round(lodOffset[lod]!.x)} m, y {Math.round(lodOffset[lod]!.y)} m</span>
        )}
      </div>

      {/* Scene */}
      <CesiumCanvas viewerRef={viewerRef}>
        {/* Render four GLBs at their chunk anchors (no interaction). */}
        {getChunksForLod(lod).map((c) => (
          // Anchor marker for visual verification
          <Entity
            key={`anchor-${c.id}`}
            position={getAnchor(c.cx, c.cy, c.ck)}
            point={{ pixelSize: 8, color: Cesium.Color.YELLOW, outlineColor: Cesium.Color.BLACK, outlineWidth: 1 }}
            label={{ text: c.id, font: '12px sans-serif', pixelOffset: new Cesium.Cartesian2(0, -14), fillColor: Cesium.Color.BLACK, showBackground: true, backgroundColor: Cesium.Color.fromAlpha(Cesium.Color.WHITE, 0.7) }}
          />
        ))}
        {getChunksForLod(lod).map((c) => (
          <ChunkModel
            key={`${lod}-${c.id}-${unitScale}-${heightOffset}`}
            spec={{
              id: `lod${lod}-${c.id}`,
              url: `/glb/lod${lod}.glb`,
              tile: { x: tx, y: ty },
              chunk: { cx: c.cx, cy: c.cy, ck: c.ck },
              zoom: ZOOM,
              origin: 'corner',
              unitScale: lod === 2 ? VOXEL_SIZE_M * 2 : lod === 3 ? VOXEL_SIZE_M * 4 : unitScale,
              localOffset: {
                x: autoOffset && lodOffset[lod]?.x ? lodOffset[lod]!.x : 0,
                y: autoOffset && lodOffset[lod]?.y ? lodOffset[lod]!.y : 0,
                z: heightOffset,
              },
              allowPicking: false,
              debug: false,
            }}
            getTileBaseHeight={getTileBaseHeight}
            onReady={(m) => {
              setReadyMap((prev) => ({ ...prev, [c.id]: true }));
              setBsMap((prev) => ({ ...prev, [c.id]: m?.boundingSphere }));
              console.info('[LOD] Model ready', {
                chunk: c,
                url: `/glb/lod${lod}.glb`, z: ZOOM, tx, ty, unitScale, heightOffset,
                hasBoundingSphere: !!m?.boundingSphere,
              });
              try {
                if (m?.boundingSphere) {
                  const anchor = getAnchor(c.cx, c.cy, c.ck);
                  const dist = Cesium.Cartesian3.distance(m.boundingSphere.center, anchor);
                  const ac = Cesium.Ellipsoid.WGS84.cartesianToCartographic(anchor);
                  const bc = Cesium.Ellipsoid.WGS84.cartesianToCartographic(m.boundingSphere.center);
                  console.info('[LOD] Anchor delta', {
                    id: c.id,
                    dist_m: Math.round(dist),
                    anchor: { lon: Cesium.Math.toDegrees(ac.longitude), lat: Cesium.Math.toDegrees(ac.latitude), h: ac.height },
                    model: { lon: Cesium.Math.toDegrees(bc.longitude), lat: Cesium.Math.toDegrees(bc.latitude), h: bc.height },
                  });
                }
              } catch {}
            }}
            onError={(e) => {
              console.error('[LOD Model] load error:', { chunk: c, url: `/glb/lod${lod}.glb`, error: e });
              setReadyMap((prev) => ({ ...prev, [c.id]: false }));
            }}
          />
        ))}
      </CesiumCanvas>
    </div>
  );
}

// Hook camera to populate HUD
// Place below component to keep patch small; in-file effect below to update HUD state.
