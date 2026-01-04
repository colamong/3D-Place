import { useEffect, useMemo, useState } from 'react';
import { useWebSocketStore } from '@/stores/useWebSocketStore';

export default function WebSocketStatus() {
  const status = useWebSocketStore((s) => s.status);
  const ws = useWebSocketStore((s) => s.ws);
  const addListener = useWebSocketStore((s) => s.addListener);

  const [lastSeq, setLastSeq] = useState<number | null>(null);
  const [lastAt, setLastAt] = useState<number | null>(null);
  const [lastCount, setLastCount] = useState<number>(0);

  useEffect(() => {
    const remove = addListener((pkg) => {
      setLastSeq(typeof pkg?.globalSeq === 'number' ? pkg.globalSeq : null);
      setLastAt(Date.now());
      try {
        const cnt = Array.isArray((pkg as any)?.events) ? (pkg as any).events.length : 0;
        setLastCount(cnt);
      } catch {
        setLastCount(0);
      }
    });
    return () => remove?.();
  }, [addListener]);

  const color = useMemo(() => {
    switch (status) {
      case 'open': return '#10B981'; // green
      case 'connecting': return '#F59E0B'; // amber
      case 'closed': return '#EF4444'; // red
      default: return '#6B7280'; // gray
    }
  }, [status]);

  const url = ws?.url || (import.meta as any)?.env?.VITE_VOXEL_WS_URL || (import.meta as any)?.env?.VITE_WS_URL || '';
  const ago = useMemo(() => lastAt ? Math.max(0, Math.round((Date.now() - lastAt)/1000)) : null, [lastAt, status]);

  return (
    <div
      style={{
        position: 'fixed',
        left: 10,
        bottom: 10,
        background: 'rgba(17,24,39,0.6)',
        color: '#E5E7EB',
        padding: '6px 10px',
        borderRadius: 6,
        fontSize: 12,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        pointerEvents: 'none',
        zIndex: 9999,
      }}
    >
      <span
        aria-label={status}
        style={{
          display: 'inline-block',
          width: 8,
          height: 8,
          borderRadius: '9999px',
          background: color,
        }}
      />
      <span style={{ opacity: 0.9 }}>
        WS {status}
        {url ? ` · ${String(url)}` : ''}
        {lastSeq != null ? ` · seq ${lastSeq}` : ''}
        {ago != null ? ` · ${ago}s ago (${lastCount})` : ''}
      </span>
    </div>
  );
}

