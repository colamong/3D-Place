import { useEffect } from 'react';
import type { ReactNode } from 'react';

type Props = {
  visible: boolean;
  onLogin: () => void;
  onClose?: () => void;
  message?: ReactNode;
  buttonLabel?: string;
};

export default function LoginToastModal({
  visible,
  onLogin,
  onClose,
  message = '로그인 후 Paint 기능을 이용할 수 있습니다.',
  buttonLabel = '로그인 하러가기',
}: Props) {
  useEffect(() => {
    if (!visible || !onClose) return undefined;

    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [visible, onClose]);

  if (!visible) return null;

  const stopPropagation = (event: React.MouseEvent) => {
    event.stopPropagation();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 70,
        padding: 24,
      }}
    >
      <div
        onClick={stopPropagation}
        style={{
          backgroundColor: '#fff',
          borderRadius: 20,
          padding: '32px 28px',
          boxShadow: '0 20px 60px rgba(15, 23, 42, 0.35)',
          maxWidth: 'min(460px, 90vw)',
          width: '100%',
          position: 'relative',
          textAlign: 'center',
        }}
      >
        {onClose && (
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            className="hover:bg-gray-100"
            style={{
              position: 'absolute',
              top: 12,
              right: 12,
              borderRadius: 999,
              border: 'none',
              width: 30,
              height: 30,
              cursor: 'pointer',
              fontSize: 14,
              fontWeight: 600,
            }}
          >
            ×
          </button>
        )}
        <div
          style={{
            color: '#111',
            fontSize: 16,
            lineHeight: 1.5,
            marginBottom: 24,
          }}
        >
          {message}
        </div>
        <button
          type="button"
          onClick={onLogin}
          className="bg-blue-600"
          style={{
            border: 'none',
            borderRadius: 999,
            padding: '12px 30px',
            color: '#fff',
            fontWeight: 600,
            fontSize: 15,
            cursor: 'pointer',
          }}
        >
          {buttonLabel}
        </button>
      </div>
    </div>
  );
}
