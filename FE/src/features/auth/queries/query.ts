import { useQuery, useMutation } from '@tanstack/react-query';
import type { UseQueryOptions, UseQueryResult } from '@tanstack/react-query';
import { authKeys } from './key';
import { getAuthState, resendVerifyEmail, logoutAllDevices } from '@/api/auth';
import type { AuthState, VerifyEmailResendResult } from '@/api/types';

export function useAuthStateQuery(
  options?: Omit<UseQueryOptions<AuthState, unknown, AuthState, ReturnType<typeof authKeys.state>>, 'queryKey' | 'queryFn'>,
): UseQueryResult<AuthState, unknown> {
  return useQuery({
    queryKey: authKeys.state(),
    queryFn: getAuthState,
    staleTime: 30_000,
    ...options,
  });
}

export function useResendVerifyEmailMutation() {
  return useMutation<VerifyEmailResendResult, unknown, string>({
    mutationFn: (email) => resendVerifyEmail(email),
  });
}

// 로그아웃: 게이트웨이 /api/logout 경로로 이동 (세션 쿠키 무효화)
export function useLogout() {
  return useMutation<void, unknown, void>({
    mutationFn: async () => {
      const gw = (import.meta.env.VITE_GATEWAY_URL ?? '').replace(/\/$/, '');
      const url = gw ? `${gw}/api/logout` : '/api/logout';

      // CSRF: Auth 서비스는 X-CSRF-TOKEN 헤더를 기대함. 쿠키 XSRF-TOKEN과 일치해야 함
      const xsrf = document.cookie
        .split(';')
        .map((v) => v.trim())
        .find((v) => v.startsWith('XSRF-TOKEN='))
        ?.split('=')[1];

      const headers: Record<string, string> = {};
      if (xsrf) {
        headers['X-CSRF-TOKEN'] = xsrf;      // 게이트웨이 내부 교환용
        headers['X-XSRF-TOKEN'] = xsrf;      // Auth CookieCsrfTokenRepository 기본 헤더명
      }

      const res = await fetch(url, {
        method: 'POST',
        credentials: 'include',
        headers: Object.keys(headers).length ? headers : undefined,
      });

      // 성공 시 JSON { frontChannelLogout: url } 를 반환
      try {
        const data = await res.json();
        const next = data?.frontChannelLogout;
        if (next) {
          window.location.assign(String(next));
          return;
        }
      } catch (_) {
        // body가 없거나 JSON이 아니면 무시하고 리로드
      }
      // 폴백: 홈으로 이동
      window.location.assign('/');
    },
  });
}

export function useLogoutAllDevicesMutation() {
  return useMutation({
    mutationFn: logoutAllDevices,
  });
}
