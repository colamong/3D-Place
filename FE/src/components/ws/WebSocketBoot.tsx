import { useWebSocketStore } from '@/stores/useWebSocketStore';
import { useEffect } from 'react';

// websocket 연결
export default function WebSocketBoot() {
  const connect = useWebSocketStore((s) => s.connect);
  const disconnect = useWebSocketStore((s) => s.disconnect);

  useEffect(() => {
    console.info('[WS] boot: connect');
    connect(); // 환경변수 없으면 ws://localhost:8087/ws
    return () => {
      console.info('[WS] boot: disconnect');
      disconnect();
    };
  }, [connect, disconnect]);

  return null;
}
