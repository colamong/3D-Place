// src/utils/voxelPayload.ts

/**
 * RGB 값을 base64로 인코딩
 */
export function rgbToBase64(r: number, g: number, b: number): string {
  const bytes = new Uint8Array([r, g, b]);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/**
 * base64를 RGB 값으로 디코딩
 */
export function base64ToRgb(base64: string): { r: number; g: number; b: number } {
  try {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return {
      r: bytes[0] ?? 255,
      g: bytes[1] ?? 255,
      b: bytes[2] ?? 255,
    };
  } catch {
    return { r: 255, g: 255, b: 255 };
  }
}