import { useEffect, useRef } from "react";
import * as Cesium from "cesium";
import { WebIO } from "@gltf-transform/core";
import { centerFromChunkIndices } from "@/features/voxels/chunk";
import { CHUNK_N } from "@/features/voxels/constants";
import type { ChunkModelSpec, GetTileBaseHeight } from "@/features/voxels/types";
import { useVoxelStateStore } from "@/stores/useVoxelStateStore";

type Props = {
  spec: ChunkModelSpec;
  getTileBaseHeight: GetTileBaseHeight;
  flatBaseHeight?: number;
  voxelSizeM: number;
};

const STORAGE_KEY = "voxel-data";

export default function GlbMockVoxelImporter({
  spec,
  getTileBaseHeight,
  flatBaseHeight = 0,
  voxelSizeM,
}: Props) {
  const pushVoxel = useVoxelStateStore((s) => s.pushVoxel);
  const pushVoxelsBatch = useVoxelStateStore((s) => s.pushVoxelsBatch);
  const clearAllVoxels = useVoxelStateStore((s) => s.clearAllVoxels);
  const voxels = useVoxelStateStore((s) => s.voxels);
  
  const hasLoadedRef = useRef(false);

  // ✅ 1. 마운트 시 localStorage 복원 → 없으면 GLB 읽기
  useEffect(() => {
    if (hasLoadedRef.current) return;
    hasLoadedRef.current = true;

    let alive = true;

    (async () => {
      // 1) localStorage 복원
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          console.log(`[GlbMockVoxelImporter] Loaded ${Object.keys(parsed).length} voxels from localStorage`);

          const voxelsToRestore = Object.values(parsed).map((voxel: any) => ({
            ...voxel,
            center: voxel.center
              ? new Cesium.Cartesian3(voxel.center.x, voxel.center.y, voxel.center.z)
              : voxel.center,
          }));

          pushVoxelsBatch(voxelsToRestore);
          return; // localStorage 경로 사용 시 종료
        }
      } catch (err) {
        console.warn('[GlbMockVoxelImporter] Failed to load from localStorage:', err);
      }

      // 2) GLB 로드
      try {
        console.log(`[GlbMockVoxelImporter] Loading GLB: ${spec.url}`);
        const io = new WebIO();
        const glb = await io.read(spec.url);
        const nodes = glb.getRoot().listNodes();
        if (!alive) return;

        console.time(`[GlbMockVoxelImporter] parse`);
        clearAllVoxels();

        const flatBaseFn: GetTileBaseHeight = () => flatBaseHeight;
        const voxelsToAdd: any[] = [];

        for (const node of nodes) {
          if (!alive) break;
          const name = node.getName?.() ?? '';
          if (!name.startsWith('node_voxel_')) continue;

          const extras = node.getExtras?.();
          if (extras) console.log(`[Node:${name}] extras →`, extras);

          const mesh = node.getMesh?.();
          if (!mesh) continue;
          const prim = mesh.listPrimitives()[0];
          if (!prim) continue;

          const pos = prim.getAttribute('POSITION')?.getArray() as Float32Array | undefined;
          const col = prim.getAttribute('COLOR_0')?.getArray() as Float32Array | undefined;
          if (!pos || pos.length < 3) continue;

          let minX = Infinity, maxX = -Infinity;
          let minY = Infinity, maxY = -Infinity;
          let minZ = Infinity, maxZ = -Infinity;
          for (let i = 0; i < pos.length; i += 3) {
            const x = pos[i], y = pos[i + 1], z = pos[i + 2];
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
          }

          const gx = Math.round((minX + maxX) * 0.5 - 0.5);
          const gy = Math.round((minY + maxY) * 0.5 - 0.5);
          const gz = Math.round((minZ + maxZ) * 0.5 - 0.5);

          const cx = Math.floor(gx / CHUNK_N);
          const cy = Math.floor(gy / CHUNK_N);
          const ck = Math.floor(gz / CHUNK_N);
          const lx = ((gx % CHUNK_N) + CHUNK_N) % CHUNK_N;
          const ly = ((gy % CHUNK_N) + CHUNK_N) % CHUNK_N;
          const lz = ((gz % CHUNK_N) + CHUNK_N) % CHUNK_N;

          const center = centerFromChunkIndices({
            tile: spec.tile,
            chunk: { cx, cy, ck },
            local: { lx, ly, lk: lz },
            getTileBaseHeight: flatBaseFn,
            zoom: spec.zoom,
          });

          let r = 255, g = 255, b = 255;
          if (col && col.length >= 3) {
            let rSum = 0, gSum = 0, bSum = 0, n = 0;
            for (let i = 0; i < col.length; i += 3) {
              rSum += col[i]; gSum += col[i + 1]; bSum += col[i + 2]; n++;
            }
            r = Math.round((rSum / n) * 255);
            g = Math.round((gSum / n) * 255);
            b = Math.round((bSum / n) * 255);
          }

          const id = `${spec.zoom}/${cx}/${cy}/${ck}/${lx}/${ly}/${lz}`;
          voxelsToAdd.push({ id, z: spec.zoom, x: cx, y: cy, k: ck, center, r, g, b });
        }

        if (alive && voxelsToAdd.length > 0) pushVoxelsBatch(voxelsToAdd);
        console.timeEnd(`[GlbMockVoxelImporter] parse`);
        console.info(`[GlbMockVoxelImporter] Imported ${voxelsToAdd.length} voxels from GLB`);
      } catch (err) {
        console.error('[GlbMockVoxelImporter] Parse failed:', err);
      }
    })();

    return () => { alive = false; };
  }, [spec, getTileBaseHeight, flatBaseHeight, voxelSizeM, pushVoxelsBatch, clearAllVoxels]);

  // ✅ 3. voxels 상태가 변경될 때마다 localStorage에 저장
  useEffect(() => {
    if (!hasLoadedRef.current) return;
    if (Object.keys(voxels).length === 0) return;

    try {
      // ✅ Cartesian3 객체를 평범한 객체로 변환
      const serializable = Object.fromEntries(
        Object.entries(voxels).map(([id, v]) => [
          id,
          {
            ...v,
            center: v.center ? { x: v.center.x, y: v.center.y, z: v.center.z } : v.center
          }
        ])
      );
      
      localStorage.setItem(STORAGE_KEY, JSON.stringify(serializable));
      console.log(`[GlbMockVoxelImporter] Saved ${Object.keys(voxels).length} voxels to localStorage`);
    } catch (err) {
      console.error("[GlbMockVoxelImporter] Failed to save to localStorage:", err);
    }
  }, [voxels]);

  return null;
}
