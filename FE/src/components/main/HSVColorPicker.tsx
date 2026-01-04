// src/components/HSVColorPicker.tsx
import { useEffect, useMemo, useRef, useState } from "react";

type Props = {
  r: number; g: number; b: number;
  onChange: (next: { r: number; g: number; b: number }) => void;
  /** true면 렌더, false면 숨김 */
  visible: boolean;
  /** 선택사항: 폭 */
  width?: number;  // default 240
  height?: number; // default 150
};

function clamp(n: number, min = 0, max = 1) { return Math.max(min, Math.min(max, n)); }
function hsvToRgb(h: number, s: number, v: number) {
  const i = Math.floor(h * 6);
  const f = h * 6 - i;
  const p = v * (1 - s);
  const q = v * (1 - f * s);
  const t = v * (1 - (1 - f) * s);
  let r=0, g=0, b=0;
  switch (i % 6) {
    case 0: r=v; g=t; b=p; break;
    case 1: r=q; g=v; b=p; break;
    case 2: r=p; g=v; b=t; break;
    case 3: r=p; g=q; b=v; break;
    case 4: r=t; g=p; b=v; break;
    case 5: r=v; g=p; b=q; break;
  }
  return { r: Math.round(r*255), g: Math.round(g*255), b: Math.round(b*255) };
}
function rgbToHsv(r: number, g: number, b: number) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r,g,b), min = Math.min(r,g,b);
  const d = max - min;
  const s = max === 0 ? 0 : d / max;
  let h = 0;
  if (d !== 0) {
    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break;
      case g: h = (b - r) / d + 2; break;
      case b: h = (r - g) / d + 4; break;
    }
    h /= 6;
  }
  return { h, s, v: max };
}

export default function HSVColorPickerInline({
  r, g, b, onChange, visible, width = 240, height = 150,
}: Props) {
  const hueW = 16; // 우측 세로 슬라이더 폭
  const pad = 10;

  const [{ h, s, v }, setHSV] = useState(() => rgbToHsv(r, g, b));
  // 외부 RGB가 바뀌면 동기화
  useEffect(() => { setHSV(rgbToHsv(r, g, b)); }, [r, g, b]);

  const hueColor = useMemo(() => {
    const { r, g, b } = hsvToRgb(h, 1, 1);
    return `rgb(${r},${g},${b})`;
  }, [h]);

  const svRef = useRef<HTMLDivElement>(null);
  const hueRef = useRef<HTMLDivElement>(null);

  // SV 영역 드래그
  useEffect(() => {
    const el = svRef.current; if (!el) return;
    const onDown = (e: MouseEvent) => {
      const rect = el.getBoundingClientRect();
      const move = (e2: MouseEvent) => {
        const nx = clamp((e2.clientX - rect.left) / rect.width);
        const ny = clamp((e2.clientY - rect.top) / rect.height);
        const next = { h, s: nx, v: 1 - ny };
        setHSV(next);
        onChange(hsvToRgb(next.h, next.s, next.v));
      };
      move(e);
      window.addEventListener("mousemove", move);
      window.addEventListener("mouseup", () => {
        window.removeEventListener("mousemove", move);
      }, { once: true });
    };
    el.addEventListener("mousedown", onDown);
    return () => el.removeEventListener("mousedown", onDown);
  }, [h, onChange]);

  // HUE 드래그
  useEffect(() => {
    const el = hueRef.current; if (!el) return;
    const onDown = (e: MouseEvent) => {
      const rect = el.getBoundingClientRect();
      const move = (e2: MouseEvent) => {
        const ny = clamp((e2.clientY - rect.top) / rect.height);
        const nextH = clamp(ny);
        const next = { h: nextH, s, v };
        setHSV(next);
        onChange(hsvToRgb(next.h, next.s, next.v));
      };
      move(e);
      window.addEventListener("mousemove", move);
      window.addEventListener("mouseup", () => {
        window.removeEventListener("mousemove", move);
      }, { once: true });
    };
    el.addEventListener("mousedown", onDown);
    return () => el.removeEventListener("mousedown", onDown);
  }, [s, v, onChange]);

  if (!visible) return null;

  return (
    <div
      style={{
        marginTop: 8,
        padding: 10,
        borderRadius: 8,
        background: "rgba(0,0,0,.45)",
        border: "1px solid rgba(255,255,255,.15)",
        display: "inline-block",
      }}
    >
      <div style={{ display: "flex", gap: 8 }}>
        {/* SV 패널 */}
        <div
          ref={svRef}
          style={{
            width, height,
            position: "relative",
            background: `linear-gradient(to top, black, transparent),
                         linear-gradient(to right, white, ${hueColor})`,
            borderRadius: 8,
            boxShadow: "0 0 0 1px rgba(0,0,0,.4) inset",
            cursor: "crosshair",
          }}
        >
          {/* 핸들 */}
          <div
            style={{
              position: "absolute",
              left: `${s * 100}%`,
              top: `${(1 - v) * 100}%`,
              transform: "translate(-50%, -50%)",
              width: 12, height: 12, borderRadius: "50%",
              background: "transparent",
              boxShadow: "0 0 0 2px white, 0 0 0 3px rgba(0,0,0,.6)",
            }}
          />
        </div>

        {/* HUE 슬라이더 */}
        <div
          ref={hueRef}
          style={{
            width: hueW,
            height,
            borderRadius: 8,
            background: `linear-gradient(
              to bottom,
              #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00
            )`,
            boxShadow: "0 0 0 1px rgba(0,0,0,.4) inset",
            position: "relative",
            cursor: "ns-resize",
          }}
        >
          <div
            style={{
              position: "absolute",
              left: -pad/2, right: -pad/2,
              top: `${h * 100}%`,
              transform: "translateY(-50%)",
              height: 4,
              borderRadius: 2,
              background: "#fff",
              boxShadow: "0 0 0 2px rgba(0,0,0,.6)",
            }}
          />
        </div>
      </div>
    </div>
  );
}
