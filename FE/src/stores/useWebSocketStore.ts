import { create } from 'zustand';

type PaintBroadcast = { globalSeq: number; events: any[] };

type Listener = (msg: PaintBroadcast) => void;

interface WebSocketState {
  ws: WebSocket | null;
  status: 'idle' | 'connecting' | 'open' | 'closed';
  autoReconnect: boolean;
  retryCount: number;
  connect: (url?: string) => void;
  disconnect: () => void;
  send: (data: unknown) => void;
  addListener: (fn: Listener) => () => void;
}

export const useWebSocketStore = create<WebSocketState>((set, get) => {
  const listeners = new Set<Listener>();
  const TAG = '[WS]';

  const notify = (msg: PaintBroadcast) => {
    listeners.forEach((fn) => fn(msg));
  };

  const connect = (url?: string) => {
    const state = get();
    const env: any = (import.meta as any).env || {};
    const wsUrl = url ?? env.VITE_VOXEL_WS_URL ?? env.VITE_WS_URL ?? 'ws://localhost:8087/ws';

    // If already connected to the same URL, do nothing.
    if (state.ws && state.ws.readyState === WebSocket.OPEN) {
      if (state.ws.url === wsUrl) return;
      // URL differs — switch connection to the requested URL.
      try { state.ws.close(); } catch { /* noop */ }
    }

    const ws = new WebSocket(wsUrl);

    set({ ws, status: 'connecting', autoReconnect: true });

    ws.onopen = () => {
      console.info(`${TAG} connect ->`, wsUrl);
      set({ status: 'open', retryCount: 0 });
    };

    ws.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data) as PaintBroadcast;
        notify(data);
        console.debug(
          `${TAG} message: globalSeq=%o events=%o`,
          data?.globalSeq,
          Array.isArray(data?.events) ? data.events.length : 0,
        );
      } catch (err) {
        console.error(`${TAG} error`, err);
      }
    };

    ws.onerror = (err) => {
      console.error('WS error', err);
    };

    ws.onclose = (ev) => {
      const code = (ev as CloseEvent)?.code;
      const reason = (ev as CloseEvent)?.reason;
      console.warn(`${TAG} close code=${code} reason=${reason || ''}`);
      const { autoReconnect, retryCount } = get();
      set({ status: 'closed', ws: null });
      if (autoReconnect) {
        const delay = Math.min(30000, 1000 * Math.pow(2, retryCount || 0));
        console.info(
          `${TAG} reconnect in ${delay}ms (retry=${(retryCount || 0) + 1})`,
        );
        setTimeout(() => {
          set({ retryCount: (get().retryCount || 0) + 1 });
          connect(wsUrl);
        }, delay);
      }
    };
  };

  const disconnect = () => {
    const { ws } = get();
    set({ autoReconnect: false });
    if (ws && ws.readyState === WebSocket.OPEN) ws.close();
    set({ ws: null, status: 'closed' });
    console.info(`${TAG} manual disconnect`);
  };

  const send = (data: unknown) => {
    const { ws } = get();
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(typeof data === 'string' ? data : JSON.stringify(data));
  };

  const addListener = (fn: Listener) => {
    listeners.add(fn);
    return () => listeners.delete(fn);
  };

  return {
    ws: null,
    status: 'idle',
    autoReconnect: true,
    retryCount: 0,
    connect,
    disconnect,
    send,
    addListener,
  };
});
