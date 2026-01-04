import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';

type ModalShellProps = {
  onClose?: () => void;
  className?: string;
  children?: React.ReactNode;
  backdropClassName?: string;
  portalTarget?: HTMLElement | null;
  contentId?: string;
};

export default function ModalShell({
  onClose,
  className = 'w-[45vw] h-[90vh] min-w-[600px] min-h-[360px]',
  children,
  backdropClassName = 'bg-black/40',
  portalTarget,
  contentId = 'app-modal-content',
}: ModalShellProps) {
  const navigate = useNavigate();
  const backdropRef = useRef<HTMLDivElement>(null);

  const close = () => (onClose ? onClose() : navigate(-1));

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      // ESC 조작 시 닫음
      if (e.key === 'Escape') close();
    }
    window.addEventListener('keydown', onKey);

    // 스크롤 잠금
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, []);

  const content = (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center"
      role="dialog"
      aria-modal="true"
    >
      <div
        ref={backdropRef}
        onClick={(e) => {
          // 바깥 클릭 시 닫기 (카드 내부 클릭 제외)
          if (e.target === backdropRef.current) close();
        }}
        className={['absolute inset-0', backdropClassName].join(' ')}
      />

      <div
        className={[
          'relative bg-white rounded-2xl shadow-2xl border border-black/5 p-6 md:p-10',
          className,
        ].join(' ')}
      >
        <button
          onClick={close}
          className="rounded-full p-2 hover:bg-gray-100 absolute right-2 top-0.5 cursor-pointer"
          aria-label="Close"
        >
          ✕
        </button>
        {/* 변경: 전역 스크롤 제거, 내부에서 명시적으로 스크롤 영역 정의 */}
        <div id={contentId} className="h-full relative overflow-hidden flex flex-col">
          {children}
        </div>
      </div>
    </div>
  );

  // body 포탈에  렌더 (cesium 캔버스 위로 보장)
  return createPortal(content, portalTarget ?? document.body);
}
