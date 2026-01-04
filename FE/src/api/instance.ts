import axios from 'axios';

import type {
  AxiosError,
  AxiosInstance,
  InternalAxiosRequestConfig,
} from 'axios';

const API_BASE = resolveApiBase(
  (import.meta.env.VITE_API_BASE ?? '/api') as string,
);

const base = {
  baseURL: API_BASE,
  withCredentials: true,
  timeout: 10_000,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-CSRF-TOKEN',
} as const;

export const api = axios.create(base);
export const adminApi = axios.create({ ...base, baseURL: `${API_BASE}/admin` });

function stripTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '');
}

function ensureLeadingSlash(value: string): string {
  if (!value.startsWith('/')) {
    return `/${value}`;
  }
  return value;
}

function resolveApiBase(rawBase: string): string {
  const fallback = '/api';
  const trimmed = (rawBase ?? '').trim();

  if (/^https?:\/\//i.test(trimmed)) {
    return stripTrailingSlash(trimmed);
  }

  const normalizedPath =
    stripTrailingSlash(ensureLeadingSlash(trimmed || fallback)) || fallback;

  if (import.meta.env.PROD) {
    const gw = ((import.meta.env.VITE_GATEWAY_URL ?? '') as string).trim();
    if (gw) {
      return `${stripTrailingSlash(gw)}${normalizedPath}`;
    }
  }

  return normalizedPath;
}

let isRefreshing = false;
let waiters: Array<(ok: boolean) => void> = [];

async function refreshSession(): Promise<boolean> {
  try {
    // 단순 재조회로 세션 확인 (서버에 별도 엔드포인트 없을 때)
    await api.get('/auth/state', { withCredentials: true });
    return true;
  } catch {
    return false;
  }
}

function attach401(i: AxiosInstance) {
  i.interceptors.response.use(
    (res) => res,
    async (err: AxiosError) => {
      const status = err.response?.status ?? 0;
      const cfg = err.config as InternalAxiosRequestConfig & {
        _retry?: boolean;
      };

      // 401이 아니거나 재시도라면 에러 반환
      if (status !== 401 || cfg?._retry) {
        return Promise.reject(err);
      }

      // 이미 애러 갱신중이라면 큐에 대기
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          waiters.push((ok) => {
            if (!ok) return reject(err);
            i.request({ ...cfg, _retry: true })
              .then(resolve)
              .catch(reject);
          });
        });
      }

      // 최초 401 처리자
      isRefreshing = true;
      cfg._retry = true;

      const ok = await refreshSession();
      isRefreshing = false;

      // 대기중이던 요청을 깨우기
      waiters.forEach((cb) => cb(ok));
      waiters = [];

      if (!ok) {
        // 세션 만료: 여기서 선택적으로 전역 로그아웃/리다이렉트 트리거 가능
        return Promise.reject(err);
      }

      // 원 요청 재시도
      try {
        return await i.request(cfg);
      } catch (e) {
        return Promise.reject(e);
      }
    },
  );
}

attach401(api);
attach401(adminApi);

// 보완: 게이트웨이가 요구하는 CSRF 헤더 자동 주입
function readCookie(name: string): string | undefined {
  return document.cookie
    .split(';')
    .map((v) => v.trim())
    .find((v) => v.startsWith(name + '='))
    ?.split('=')[1];
}

function attachCsrf(i: AxiosInstance) {
  i.interceptors.request.use((cfg) => {
    // Spring Security 기본: cookie 'XSRF-TOKEN' + header 'X-XSRF-TOKEN'
    // 필요 시 VITE_CSRF_HEADER=only-xxsrf 로 'X-CSRF-TOKEN' 미전송
    const headerMode = (import.meta as any)?.env?.VITE_CSRF_HEADER ?? 'both';
    const token = readCookie('XSRF-TOKEN');
    if (!token) return cfg;

    cfg.headers ||= {} as any;
    if (headerMode === 'only-xxsrf') {
      (cfg.headers as any)['X-XSRF-TOKEN'] = token;
    } else {
      // 기본은 호환성 위해 둘 다 전송
      (cfg.headers as any)['X-XSRF-TOKEN'] =
        (cfg.headers as any)['X-XSRF-TOKEN'] || token;
      (cfg.headers as any)['X-CSRF-TOKEN'] =
        (cfg.headers as any)['X-CSRF-TOKEN'] || token;
    }
    return cfg;
  });
}

attachCsrf(api);
attachCsrf(adminApi);
