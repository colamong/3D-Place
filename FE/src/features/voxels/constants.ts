// src/features/voxels/constants.ts
export const OVERLAY_IDS = new Set([
  '__overlay_highlight',
  '__ghost_draft',
  '__overlay_grid',
]);
export const ZOOM = 9;
export const GAP = 0;
export const MAX_DRAFTS = 64;

// ecef
export const VOXEL_SIZE_M = 100;
export const USE_ECEF_GRID = true;
export const BASE_LIFT = 0.5;
export const VOXEL_SINK_M = 0;

//chunk
export const CHUNK_N = 256;

// grid
export const GRID_ALIGN = 'edges' as const;
export const GRID_STEP_M = VOXEL_SIZE_M;
export const CENTER_OFFSET_M = VOXEL_SIZE_M * 0.5;
export const GRID_LINE_OFFSET_M = 0;

// color
export const PALETTE: [number, number, number][] = [
  [255, 110, 110],
  [255, 173, 41],
  [255, 232, 94],
  [140, 220, 60],
  [69, 199, 232],
  [91, 131, 255],
  [168, 121, 255],
  [255, 125, 208],
  [255, 255, 255],
  [230, 230, 230],
  [160, 160, 160],
  [45, 45, 45],
];

// color utils
export const clampByte = (n: number) =>
  Math.max(0, Math.min(255, Math.round(n)));
export const toHex2 = (n: number) =>
  clampByte(n).toString(16).padStart(2, '0').toUpperCase();
export const rgbToHex = (r: number, g: number, b: number) =>
  `${toHex2(r)}${toHex2(g)}${toHex2(b)}`;

// Grid visibility thresholds
// - When zooming out: auto-off triggers at or above GRID_DISABLE_HEIGHT_M
// - When zooming in: button re-enables below GRID_ENABLE_HEIGHT_M
//   (ENABLE > DISABLE to provide hysteresis so 한 칸 확대만으로도 다시 켤 수 있음)
export const GRID_DISABLE_HEIGHT_M = 40;
export const GRID_HYSTERESIS_M = 0; // no height hysteresis
export const GRID_ENABLE_HEIGHT_M = GRID_DISABLE_HEIGHT_M; // single threshold
// Backward compatibility (older code may import this)
export const GRID_AUTO_OFF_HEIGHT_M = GRID_DISABLE_HEIGHT_M;

// Pixel-based thresholds (preferred for UX consistency)
// 기준: "한 보셀(VOXEL_SIZE_M)"이 화면에서 차지하는 픽셀 크기
// 내부 로직에서 pxPerVoxel = pxPerMeter * VOXEL_SIZE_M 으로 변환 후 비교합니다.
// - disable when pxPerVoxel < GRID_DISABLE_PX
// - enable  when pxPerVoxel >= GRID_ENABLE_PX
export const GRID_DISABLE_PX = 5; // px per voxel
export const GRID_ENABLE_PX = 5;  // single threshold (no hysteresis)

export const VOXEL_ALPHA = 80;

// eraser limits
export const ERASER_MAX_CHARGES = 64; // max available eraser charges
export const ERASER_REGEN_INTERVAL_MS = 10000; // +1 charge per 10 seconds

// painter limits (same as eraser by default)
export const PAINT_MAX_CHARGES = 64; // max available paint charges
export const PAINT_REGEN_INTERVAL_MS = 10000; // +1 charge per 10 seconds
