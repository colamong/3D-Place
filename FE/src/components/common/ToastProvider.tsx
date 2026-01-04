import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import type { ReactNode } from 'react';

export type ToastVariant = 'info' | 'success' | 'warning' | 'error';

export interface ToastOptions {
  variant?: ToastVariant;
  duration?: number;
}

type ToastEntry = {
  id: number;
  message: string;
  variant: ToastVariant;
  duration: number;
};

type ToastContextValue = {
  showToast: (message: string, options?: ToastOptions) => void;
};

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

const DEFAULT_DURATION = 4000;

const VARIANT_STYLES: Record<ToastVariant, { background: string; color: string; border: string }> =
  {
    info: {
      background: 'linear-gradient(135deg, #0ea5e9, #2563eb)',
      border: '1px solid rgba(14, 165, 233, 0.5)',
      color: '#fff',
    },
    success: {
      background: 'linear-gradient(135deg, #22c55e, #16a34a)',
      border: '1px solid rgba(34, 197, 94, 0.5)',
      color: '#fff',
    },
    warning: {
      background: 'linear-gradient(135deg, #fbbf24, #f97316)',
      border: '1px solid rgba(251, 191, 36, 0.5)',
      color: '#111',
    },
    error: {
      background: 'linear-gradient(135deg, #ef4444, #b91c1c)',
      border: '1px solid rgba(239, 68, 68, 0.5)',
      color: '#fff',
    },
  };

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const idSequence = useRef(0);

  const showToast = useCallback((message: string, options?: ToastOptions) => {
    const variant = options?.variant ?? 'info';
    const duration = options?.duration ?? DEFAULT_DURATION;
    const id = ++idSequence.current;

    setToasts((current) => [...current, { id, message, variant, duration }]);
  }, []);

  const removeToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div
        style={{
          position: 'fixed',
          right: 24,
          bottom: 24,
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          alignItems: 'flex-end',
          pointerEvents: 'none',
          zIndex: 60,
        }}
      >
        {toasts.map((toast) => (
          <ToastCard key={toast.id} toast={toast} onDismiss={() => removeToast(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastCard({
  toast,
  onDismiss,
}: {
  toast: ToastEntry;
  onDismiss: () => void;
}) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, toast.duration);
    return () => clearTimeout(timer);
  }, [toast.duration, onDismiss]);

  const style = VARIANT_STYLES[toast.variant];

  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        background: style.background,
        border: style.border,
        color: style.color,
        padding: '12px 16px',
        borderRadius: 16,
        minWidth: 260,
        maxWidth: 'min(360px, 90vw)',
        boxShadow: '0 15px 40px rgba(15, 23, 42, 0.25)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        pointerEvents: 'auto',
      }}
    >
      <span style={{ fontSize: 14, lineHeight: 1.4 }}>{toast.message}</span>
      <button
        type="button"
        aria-label="Close"
        onClick={onDismiss}
        style={{
          border: 'none',
          background: 'rgba(0,0,0,0.15)',
          color: '#fff',
          borderRadius: 999,
          width: 28,
          height: 28,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          fontSize: 16,
        }}
      >
        x
      </button>
    </div>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
