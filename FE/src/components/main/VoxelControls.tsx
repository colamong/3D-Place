// src/components/VoxelControls.tsx
import { useState, useEffect, useCallback } from 'react';
import HSVColorPicker from './HSVColorPicker';
import { PALETTE, rgbToHex } from '@/features/voxels/constants';

export default function VoxelControls(props: {
  r: number;
  g: number;
  b: number;
  setR: (n: number) => void;
  setG: (n: number) => void;
  setB: (n: number) => void;
  showGrid: boolean;
  setShowGrid: (b: boolean) => void;
  gridWidth: number;
  setGridWidth: (n: number) => void;
  embedded?: boolean;
  onUnlock: () => void;
  onClose?: () => void;
}) {
  const {
    r,
    g,
    b,
    setR,
    setG,
    setB,
    embedded = false,
  } = props;

  const [pickerOpen, setPickerOpen] = useState(false);
  const hexNow = `#${rgbToHex(r, g, b)}`;
  const [hexEdit, setHexEdit] = useState(hexNow);
  const [hexFocused, setHexFocused] = useState(false);

  // 최근 색상 1칸(영속)
  type RGB = [number, number, number];
  const RECENT_KEY = 'ui:recentColor';
  const [recent, setRecent] = useState<RGB | null>(() => {
    try {
      const raw = localStorage.getItem(RECENT_KEY);
      if (!raw) return null;
      const v = JSON.parse(raw);
      if (Array.isArray(v) && v.length === 3) return [v[0], v[1], v[2]] as RGB;
      return null;
    } catch {
      return null;
    }
  });
  const setRecentSafe = useCallback((c: RGB) => {
    setRecent(c);
    try {
      localStorage.setItem(RECENT_KEY, JSON.stringify(c));
    } catch {}
  }, []);

  useEffect(() => {
    if (!hexFocused) setHexEdit(hexNow);
  }, [hexNow, hexFocused]);

  const commitHex = useCallback(() => {
    const v = hexEdit.replace('#', '').trim().toUpperCase();
    if (/^[0-9A-F]{6}$/.test(v)) {
      const rr = parseInt(v.slice(0, 2), 16);
      const gg = parseInt(v.slice(2, 4), 16);
      const bb = parseInt(v.slice(4, 6), 16);
      setR(rr);
      setG(gg);
      setB(bb);
      setHexEdit(`#${v}`);
      setRecentSafe([rr, gg, bb]);
    } else {
      setHexEdit(hexNow);
    }
  }, [hexEdit, hexNow, setR, setG, setB, setRecentSafe]);

  // 피커 닫힐 때 최근 색상으로 기록
  useEffect(() => {
    if (!pickerOpen) setRecentSafe([r, g, b]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pickerOpen]);

  // ★ embedded 여부에 따라 컨테이너 스타일만 바뀜
  const containerStyle: React.CSSProperties = embedded
    ? {
        position: 'static',
        background: 'transparent',
        color: '#111',
        padding: '10px 12px',
        borderRadius: 8,
        fontSize: 12,
        minWidth: 260,
      }
    : {
        position: 'absolute',
        left: 12,
        top: 12,
        zIndex: 10,
        background: 'rgba(0,0,0,.55)',
        color: '#fff',
        padding: '10px 12px',
        borderRadius: 8,
        fontSize: 12,
        minWidth: 260,
      };

  return (
    <div style={containerStyle}>
      {/* Eraser UI moved to MainPage action row */}
      {/* RGB 슬라이더 */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '24px 1fr 54px',
          gap: 6,
          alignItems: 'center',
        }}
      >
        <div>R</div>
        <input
          type="range"
          min={0}
          max={255}
          value={r}
          onChange={(e) => setR(+e.target.value)}
        />
        <input
          type="number"
          min={0}
          max={255}
          value={r}
          onChange={(e) => setR(+e.target.value)}
        />
        <div>G</div>
        <input
          type="range"
          min={0}
          max={255}
          value={g}
          onChange={(e) => setG(+e.target.value)}
        />
        <input
          type="number"
          min={0}
          max={255}
          value={g}
          onChange={(e) => setG(+e.target.value)}
        />
        <div>B</div>
        <input
          type="range"
          min={0}
          max={255}
          value={b}
          onChange={(e) => setB(+e.target.value)}
        />
        <input
          type="number"
          min={0}
          max={255}
          value={b}
          onChange={(e) => setB(+e.target.value)}
        />
      </div>

      {/* 미니 스와치 + HEX */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          marginTop: 10,
        }}
      >
        <button
          onClick={() => setPickerOpen((v) => !v)}
          title="색상 선택"
          style={{
            width: 28,
            height: 28,
            borderRadius: 6,
            cursor: 'pointer',
            background: `rgb(${r},${g},${b})`,
            border: '1px solid rgba(255,255,255,.3)',
            boxShadow: '0 0 0 1px rgba(0,0,0,.25) inset',
          }}
          aria-label="Open color picker"
        />
        <label style={{ fontSize: 12, opacity: 0.85 }}>HEX</label>
        <input
          type="text"
          value={hexEdit}
          onChange={(e) => setHexEdit(e.target.value.toUpperCase())}
          onFocus={() => setHexFocused(true)}
          onBlur={() => {
            setHexFocused(false);
            commitHex();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') (e.currentTarget as HTMLInputElement).blur();
          }}
          style={{
            width: 90,
            padding: '4px 6px',
            borderRadius: 6,
            border: embedded ? '1px solid rgba(0,0,0,.2)' : '1px solid rgba(255,255,255,.25)',
            background: embedded ? '#fff' : 'rgba(255,255,255,.06)',
            color: embedded ? '#111' : '#fff',
            fontFamily: 'monospace',
          }}
        />
      </div>

      {/* 펼쳐지는 인라인 피커 */}
      <HSVColorPicker
        r={r}
        g={g}
        b={b}
        visible={pickerOpen}
        onChange={({ r: rr, g: gg, b: bb }) => {
          setR(rr);
          setG(gg);
          setB(bb);
        }}
        width={260}
        height={160}
      />

      {/* 팔레트 */}
      <div style={{ marginTop: 8 }}>
        <div style={{ fontSize: 12, marginBottom: 6, opacity: 0.85 }}>
          Palette
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(11, 22px)',
            gap: 6,
          }}
        >
          {/* 최근 색상 1칸 */}
          <button
            key="recent"
            title={recent ? `#${rgbToHex(recent[0], recent[1], recent[2])}` : 'Recent'}
            onClick={() => {
              if (!recent) return;
              const [rr, gg, bb] = recent;
              setR(rr);
              setG(gg);
              setB(bb);
            }}
            style={{
              width: 22,
              height: 22,
              borderRadius: 6,
              cursor: recent ? 'pointer' : 'default',
              background: recent ? `rgb(${recent[0]},${recent[1]},${recent[2]})` : 'transparent',
              border: embedded
                ? '1px solid rgba(0,0,0,.2)'
                : '1px solid rgba(255,255,255,.25)',
              boxShadow:
                recent && r === recent[0] && g === recent[1] && b === recent[2]
                  ? embedded
                    ? '0 0 0 2px #111 inset'
                    : '0 0 0 2px #fff inset'
                  : '0 0 0 1px rgba(0,0,0,.3) inset',
            }}
            aria-label="Recent color"
            disabled={!recent}
          />
          {PALETTE.slice(0, 10).map(([pr, pg, pb], i) => (
            <button
              key={i}
              title={`#${rgbToHex(pr, pg, pb)}`}
              onClick={() => {
                setR(pr);
                setG(pg);
                setB(pb);
                setRecentSafe([pr, pg, pb]);
              }}
              style={{
                width: 22,
                height: 22,
                borderRadius: 6,
                cursor: 'pointer',
                background: `rgb(${pr},${pg},${pb})`,
                border: embedded
                  ? '1px solid rgba(0,0,0,.2)'
                  : '1px solid rgba(255,255,255,.25)',
                boxShadow:
                  r === pr && g === pg && b === pb
                    ? embedded
                      ? '0 0 0 2px #111 inset'
                      : '0 0 0 2px #fff inset'
                    : '0 0 0 1px rgba(0,0,0,.3) inset',
              }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
