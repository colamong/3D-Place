import React, { useCallback, useEffect, useRef, useState } from 'react';
import RoundBtn from '../common/RoundBtn';
import {
  Pen,
  Pipette,
  MousePointerClick,
  PanelRightDashed,
} from 'lucide-react';

export type Mode = 'paint' | 'erase' | 'eyedropper' | 'select';

export type ModeSelectorPanelProps = {
  id?: string; // persistence key; default "mode-selector"
  initial?: { x: number; y: number }; // default {x: 16, y: 16}
  onModeChange?: (mode: Mode) => void;
  value?: Mode; // controlled
  defaultValue?: Mode; // uncontrolled
  disablePersistence?: boolean;
  placement?: 'overlay' | 'dock';
  onPlacementChange?: (p: 'overlay' | 'dock') => void;
  hotkeysEnabled?: boolean; // disable to avoid conflicts while hidden
  defaultExpanded?: boolean;
  autoCollapse?: boolean;
  animationMs?: number;
};

const clamp = (v: number, min: number, max: number) =>
  Math.min(max, Math.max(min, v));

function usePersistentXY(
  key: string,
  initial: { x: number; y: number },
  disabled?: boolean,
) {
  const [pos, setPos] = useState(initial);
  useEffect(() => {
    if (disabled) return;
    try {
      const raw = localStorage.getItem(`ui:pos:${key}`);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (typeof parsed?.x === 'number' && typeof parsed?.y === 'number') {
          setPos(parsed);
        }
      }
    } catch {}
  }, [key, disabled]);
  useEffect(() => {
    if (disabled) return;
    try {
      localStorage.setItem(`ui:pos:${key}`, JSON.stringify(pos));
    } catch {}
  }, [key, pos, disabled]);
  return [pos, setPos] as const;
}

export default function ModeSelectorPanel({
  id = 'mode-selector',
  initial = { x: 16, y: 16 },
  onModeChange,
  value,
  defaultValue = 'paint',
  disablePersistence,
  placement = 'overlay',
  onPlacementChange,
  hotkeysEnabled = true,
  defaultExpanded = false,
  autoCollapse = true,
  animationMs = 200,
}: ModeSelectorPanelProps) {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const dragging = useRef(false);
  const dragOffset = useRef({ x: 0, y: 0 });
  const shiftRef = useRef(false);
  const [xy, setXY] = usePersistentXY(id, initial, disablePersistence);
  const [mode, setMode] = useState<Mode>(defaultValue);
  // UI는 erase 상태를 하이라이트하지 않음(패널에서 제거됨)
  const currentForUI: Mode =
    (value ?? mode) === 'erase' ? mode : (value ?? mode);

  // Keep inside viewport on resize
  useEffect(() => {
    const onResize = () => {
      const el = panelRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const maxX = window.innerWidth - rect.width;
      const maxY = window.innerHeight - rect.height;
      setXY((p) => ({ x: clamp(p.x, 0, maxX), y: clamp(p.y, 0, maxY) }));
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [setXY]);

  // Snap calc
  const snap = useCallback(
    (nx: number, ny: number, w: number, h: number, disableSnap: boolean) => {
      if (disableSnap) return { x: nx, y: ny };
      const edge = 12; // edge snap px
      const grid = 8; // grid snap px

      const maxX = window.innerWidth - w;
      const maxY = window.innerHeight - h;
      let x = clamp(nx, 0, maxX);
      let y = clamp(ny, 0, maxY);

      // Edge snap
      if (x <= edge) x = 0;
      else if (Math.abs(x - maxX) <= edge) x = maxX;
      if (y <= edge) y = 0;
      else if (Math.abs(y - maxY) <= edge) y = maxY;

      // Grid snap
      x = Math.round(x / grid) * grid;
      y = Math.round(y / grid) * grid;

      return { x, y };
    },
    [],
  );

  const onPointerDown = useCallback((e: React.PointerEvent) => {
    const el = panelRef.current;
    if (!el) return;
    // 버튼 등 인터랙티브 요소 위에서는 드래그 시작 안 함
    const target = e.target as HTMLElement | null;
    if (target && target.closest('button')) return;
    const rect = el.getBoundingClientRect();
    dragging.current = true;
    dragOffset.current = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    e.stopPropagation();
    e.preventDefault();
  }, []);

  const onPointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (!dragging.current) return;
      const el = panelRef.current;
      if (!el) return;
      const w = el.offsetWidth,
        h = el.offsetHeight;
      const nx = e.clientX - dragOffset.current.x;
      const ny = e.clientY - dragOffset.current.y;
      const { x, y } = snap(nx, ny, w, h, shiftRef.current); // Shift: 스냅 해제
      setXY({ x, y });
      e.stopPropagation();
      e.preventDefault();
    },
    [setXY, snap],
  );

  const onPointerUp = useCallback((e: React.PointerEvent) => {
    dragging.current = false;
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {}
    e.stopPropagation();
    e.preventDefault();
  }, []);

  const choose = useCallback(
    (m: Mode) => {
      if (value === undefined) setMode(m);
      onModeChange?.(m);
    },
    [onModeChange, value],
  );

  const Btn = ({
    m,
    title,
    children,
  }: {
    m: Mode;
    title: string;
    children: React.ReactNode;
  }) => (
    <RoundBtn
      aria-pressed={currentForUI === m}
      onClick={() => choose(m)}
      title={title}
    >
      {children}
    </RoundBtn>
  );

  // Keyboard shortcuts when panel is visible
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!hotkeysEnabled) return;
      // ignore if typing in inputs/contentEditable or with modifiers
      const t = e.target as HTMLElement | null;
      const tag = (t?.tagName || '').toLowerCase();
      const typing =
        tag === 'input' ||
        tag === 'textarea' ||
        tag === 'select' ||
        (t as any)?.isContentEditable;
      if (typing || e.ctrlKey || e.metaKey || e.altKey) return;
      switch (e.key.toLowerCase()) {
        case 'q':
          choose('paint');
          break;
        case 'c':
          choose('eyedropper');
          break;
        case 's':
          choose('select');
          break;
        default:
          return;
      }
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [choose, hotkeysEnabled]);

  // 도크 모드에서는 오버레이 패널을 그리지 않음
  if (placement === 'dock') return null;

  return (
    <div
      ref={panelRef}
      style={{ position: 'fixed', left: xy.x, top: xy.y }}
      className="z-50 pointer-events-auto bg-white text-black border border-black/10 rounded-xl shadow-xl select-none"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
    >
      {/* Shift 상태 추적 */}
      <KeyTracker
        onChange={(s) => {
          shiftRef.current = s.shift;
        }}
      />

      {/* controls: 항상 펼친 상태, 컴팩트 레이아웃 */}
      <div className="px-5 py-2">
        <div className="flex items-center gap-2">
          <Btn m="paint" title="Paint">
            <Pen />
          </Btn>
          <Btn m="select" title="Select">
            <MousePointerClick />
          </Btn>
          <Btn m="eyedropper" title="Pipette">
            <Pipette />
          </Btn>
          <RoundBtn
            onClick={() => onPlacementChange?.('dock')}
            title="Move to right dock"
          >
            <PanelRightDashed />
          </RoundBtn>
        </div>
      </div>
    </div>
  );
}

function KeyTracker({
  onChange,
}: {
  onChange: (s: { shift: boolean }) => void;
}) {
  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === 'Shift') onChange({ shift: true });
    };
    const up = (e: KeyboardEvent) => {
      if (e.key === 'Shift') onChange({ shift: false });
    };
    window.addEventListener('keydown', down);
    window.addEventListener('keyup', up);
    return () => {
      window.removeEventListener('keydown', down);
      window.removeEventListener('keyup', up);
    };
  }, [onChange]);
  return null;
}
