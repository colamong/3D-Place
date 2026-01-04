// src/components/ColorToast.tsx
import { useEffect } from 'react';
import type { CSSProperties, ReactNode } from 'react';

type Anchor = 'br' | 'bl' | 'tr' | 'tl' | 'rc' | 'bc';

type Props = {
  visible: boolean;
  onClose: () => void;
  anchor?: Anchor; // ★ 앵커
  offset?: { x?: number; y?: number }; // ★ 여백
  width?: number;
  children?: ReactNode;
  style?: CSSProperties; // (옵션) 추가 커스터마이즈
  showHeaderClose?: boolean;
};

export default function ColorToast({
  visible,
  onClose,
  anchor = 'br',
  offset = { x: 24, y: 24 },
  width = 330,
  children,
  style,
}: Props) {
  if (!visible) return null;

  // ESC로 닫기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const x = offset.x ?? 24;
  const y = offset.y ?? 24;

  const pos: CSSProperties = { position: 'absolute', zIndex: 20 };

  switch (anchor) {
    case 'rc':
      pos.top = '50%';
      pos.right = x;
      pos.transform = 'translateY(-50%)';
      break;
    case 'br':
      pos.right = x;
      pos.bottom = y;
      break;
    case 'tr':
      pos.right = x;
      pos.top = y;
      break;
    case 'bl':
      pos.left = x;
      pos.bottom = y;
      break;
    case 'tl':
      pos.left = x;
      pos.top = y;
      break;
    case 'bc':
      pos.left = '50%';
      pos.bottom = y;
      pos.transform = 'translateX(-50%)';
      break;
  }

  return (
    <div style={{ ...pos, ...style }}>
      <div
        style={{
          width,
          maxHeight: 'min(80vh, 520px)',
          overflow: 'auto',
          background: '#fff',
          color: '#111',
          borderRadius: 12,
          padding: 12,
          border: '1px solid rgba(0,0,0,.08)',
          boxShadow: '0 10px 30px rgba(0,0,0,.20)',
          position: 'relative',
        }}
        className="scrollbar-none"
      >
        <button
          onClick={onClose}
          aria-label="Close"
          style={{
            position: 'absolute',
            right: 6,
            top: 6,
            borderRadius: 999,
            padding: 4,
            border: 'none',
            background: 'transparent',
            cursor: 'pointer',
          }}
        >
          ✕
        </button>

        {children}
      </div>
    </div>
  );
}
