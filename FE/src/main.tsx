import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import { BrowserRouter } from 'react-router-dom';

import * as Cesium from 'cesium';
import 'cesium/Build/Cesium/Widgets/widgets.css';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import WebSocketBoot from './components/ws/WebSocketBoot.tsx';
import { ToastProvider } from '@/components/common/ToastProvider';

Cesium.Ion.defaultAccessToken = import.meta.env.VITE_ION_TOKEN;

// msw 활성화 함수 (DEV에서만, 플래그로 제어)
async function enableMocking() {
  if (!import.meta.env.DEV) return;
  const useMock = (import.meta.env.VITE_USE_MSW ?? 'true') !== 'false';
  if (!useMock) return;
  const { worker } = await import('@/mocks/browser');
  return worker.start({ onUnhandledRequest: 'bypass' });
}

// Tanstack Query
const qc = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 3,
      staleTime: 30_000,
    },
  },
});

const root = createRoot(document.getElementById('root')!);

enableMocking().then(() => {
  root.render(
    <StrictMode>
      <QueryClientProvider client={qc}>
        <ToastProvider>
          <BrowserRouter>
            <WebSocketBoot />
            <App />
          </BrowserRouter>
        </ToastProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
});
