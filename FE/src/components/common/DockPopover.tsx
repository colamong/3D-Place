import { cn } from '@/lib/cn';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

type Placement = 'right' | 'left' | 'bottom' | 'top';

type Props = {
  anchorRef: React.RefObject<HTMLElement>;
  onClose: () => void;
  placement?: Placement;
  offset?: number; // 앵커와 간격
  portalTarget?: HTMLElement | null;
  className?: string;
  children?: React.ReactNode;
  withArrow?: boolean;
};

export default function DockPopover({
  anchorRef,
  onClose,
  placement = 'left',
  offset = 8,
  portalTarget,
  className,
  children,
  withArrow = true,
}: Props) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [style, setStyle] = useState<React.CSSProperties>({ opacity: 0 });

  // ESC 닫기 + 바깥 클릭 닫기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();

    const onPointerDown = (e: PointerEvent) => {
      const p = panelRef.current;
      const a = anchorRef.current;
      if (!p || !a) return;
      const t = e.target as Node;
      // 패널 내부나 앵커 내부면 무시, 그 외면 닫기
      if (!p.contains(t) && !a.contains(t)) onClose();
    };

    window.addEventListener('keydown', onKey);
    // ⬇️ 캡처 단계에서 먼저 받기 (Cesium/타 컴포넌트가 중간에 막아도 안전)
    document.addEventListener('pointerdown', onPointerDown, { capture: true });

    return () => {
      window.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointerDown, {
        capture: true,
      } as any);
    };
  }, [onClose, anchorRef]);

  // 위치 계산
  useLayoutEffect(() => {
    const a = anchorRef.current;
    const p = panelRef.current;
    if (!a || !p) return;

    // 1) 앵커/패널의 viewport 기준 rect
    const ar = a.getBoundingClientRect();
    const { width: pw, height: ph } = p.getBoundingClientRect();

    // 2) 포털 타깃 메트릭(숫자만)
    const targetEl =
      portalTarget ??
      document.getElementById('editor-viewport') ??
      document.body;

    let trLeft = 0;
    let trTop = 0;
    let trWidth = window.innerWidth;
    let trHeight = window.innerHeight;

    if (targetEl !== document.body) {
      const r = (targetEl as HTMLElement).getBoundingClientRect();
      trLeft = r.left;
      trTop = r.top;
      trWidth = r.width;
      trHeight = r.height;
    }

    // 3) viewport 기준 원하는 위치
    let topVP = 0,
      leftVP = 0;
    switch (placement) {
      case 'right':
        topVP = ar.top + ar.height / 2 - ph / 2;
        leftVP = ar.right + offset;
        break;
      case 'left':
        topVP = ar.top + ar.height / 2 - ph / 2;
        leftVP = ar.left - pw - offset;
        break;
      case 'bottom':
        topVP = ar.bottom + offset;
        leftVP = ar.left + ar.width / 2 - pw / 2;
        break;
      case 'top':
        topVP = ar.top - ph - offset;
        leftVP = ar.left + ar.width / 2 - pw / 2;
        break;
    }

    // 4) 포털 타깃 좌표계로 변환
    let left = leftVP;
    let top = topVP;

    if (targetEl === document.body) {
      left += window.scrollX;
      top += window.scrollY;
    } else {
      left -= trLeft;
      top -= trTop;
    }

    // 5) 타깃 경계 안으로 클램프
    left = Math.max(8, Math.min(left, trWidth - pw - 8));
    top = Math.max(8, Math.min(top, trHeight - ph - 8));

    setStyle({ position: 'absolute', left, top, opacity: 1 });
  }, [anchorRef, portalTarget, placement, offset]);

  const target =
    portalTarget ?? document.getElementById('editor-viewport') ?? document.body;

  const arrow = withArrow && (
    <div
      className={cn(
        'absolute w-3 h-3 bg-white rotate-45 border border-black/5',
        placement === 'left' && 'right-[-6px] top-1/2 -translate-y-1/2',
        placement === 'right' && 'left-[-6px] top-1/2 -translate-y-1/2',
        placement === 'top' && 'bottom-[-6px] left-1/2 -translate-x-1/2',
        placement === 'bottom' && 'top-[-6px] left-1/2 -translate-x-1/2',
      )}
    />
  );

  const node = (
    <div
      ref={panelRef}
      style={style}
      className={cn(
        'z-[60] bg-white rounded-xl shadow-xl border border-black/5',
        'p-4 min-w-[140px] max-w-[500px]',
        'animate-in fade-in zoom-in-95 duration-150',
        className,
      )}
    >
      {arrow}
      {children}
    </div>
  );

  return createPortal(node, target);
}
