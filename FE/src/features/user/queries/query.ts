import {
  getMyProfile,
  getPublicProfileByHandle,
  updateProfile,
  deleteAccount,
} from '@/api/user';

import { useAuthStateQuery } from '@/features/auth/queries/query';
import type { UpdateProfileRequestBody } from '@/api/types';
import { userKeys } from './key';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

export function useMeQuery(options?: any): any {
  return useQuery({
    queryKey: userKeys.me(),
    queryFn: getMyProfile,
    staleTime: 30_000,
    ...options,
  });
}

export function usePublicProfileQuery(
  nicknameHandle: string,
  options?: any,
): any {
  return useQuery({
    queryKey: userKeys.publicByHandle(nicknameHandle),
    queryFn: () => getPublicProfileByHandle(nicknameHandle),
    enabled: !!nicknameHandle,
    staleTime: 30_000,
    ...options,
  });
}

// admin-only user profile/identities/events hooks were moved to features/admin/queries/query.ts

export function useUpdateProfileMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateProfileRequestBody) => updateProfile(body),
    onSuccess: (res) => {
      // ??????꿔꺂?ｉ뜮戮녹춹???熬곣뫖利?????? ?????욍걛???ш끽維?? ?????類ㅺ튃????됰Ŧ?뤻툣??????
      try {
        qc.setQueryData(userKeys.me(), res);
      } catch (_) {
        // ignore
      }
      qc.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}

// identity link/unlink hooks removed (use new startLink/oauth callbacks if needed)\r\n
export function useDeleteAccountMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}

// Compatibility hook for existing code expecting `useLogin` from user queries
export function useLogin() {
  const { data, isLoading, refetch } = useAuthStateQuery();
  const authenticated = Boolean(data?.authenticated);

  // mutation-like API expected by existing components
  const mutate = (next?: string) => {
    const gw = (import.meta.env.VITE_GATEWAY_URL ?? '').replace(/\/$/, '');
    const base = gw ? `${gw}/api/login` : '/api/login';
    const url = next ? `${base}?next=${encodeURIComponent(next)}` : base;
    window.location.assign(url);
  };

  return {
    mutate,
    isPending: false as const,
    authenticated,
    isLoading,
    state: data,
    refetch,
  } as const;
}
