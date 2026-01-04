import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getAdminUserDetail, getAdminUserIdentities, getAdminUserEvents } from '@/api/admin';
import type { CommonResponse, UserProfileResponse, UserIdentityDto, AuthEventResponse, AdminUserEventsParams } from '@/api/types';
import { opsKeys } from './key';
import type { ModuleId, OpsDashboardPayload } from '@/types/admin';
import { fetchOpsDashboard, reprocessDlq } from '@/api/admin';
import { useAdminStore } from '@/stores/useAdminStore';
import { useEffect, useRef } from 'react';

// ---- normalize helpers ----
const emptyKpis = (): OpsDashboardPayload['kpis'] => ({
  all: [],
  gateway: [],
  ws: [],
  paint: [],
  world: [],
  user: [],
  report: [],
  worker: [],
});

function normalizePayload(
  raw?: Partial<OpsDashboardPayload>,
): OpsDashboardPayload {
  const base = {
    kpis: { ...emptyKpis(), ...(raw?.kpis ?? {}) },
    moduleHealth: raw?.moduleHealth ?? [],
    consumerRateSeries: raw?.consumerRateSeries ?? [],
    consumerSnapshot: raw?.consumerSnapshot ?? [],
    dlq: raw?.dlq ?? [],
    users: raw?.users ?? [],
    reports: raw?.reports ?? [],
  };

  const mods: Exclude<ModuleId, 'all'>[] = [
    'gateway',
    'ws',
    'paint',
    'world',
    'user',
    'report',
    'worker',
  ];
  if (!base.kpis.all?.length) {
    base.kpis.all = mods.flatMap((m) => base.kpis[m] ?? []);
  }

  return base;
}

// ---- hooks (UI?먯꽌 諛붾줈 ?ъ슜) ----
export function useOpsDashboard(
  scope: ModuleId,
  opts?: {
    // ?대쭅/?좎꽑??媛?쒖꽦??????듭뀡留??몄텧
    refetchIntervalMs?: number | false;
    staleTimeMs?: number;
  },
) {
  const {
    refetchIntervalMs = 30_000, // ?깍툘 ?대쭅(visible???뚮쭔)
    staleTimeMs = 5_000,
  } = opts ?? {};

  // zustand setter瑜?ref濡?怨좎젙(?섏〈??寃쎄퀬/?ъ깮??諛⑹?)
  const setPayloadRef = useRef(useAdminStore.getState().setPayload);

  const query = useQuery({
    queryKey: opsKeys.dashboard(scope),
    queryFn: ({ signal }) => fetchOpsDashboard(scope, signal),
    placeholderData: (prev) => prev ?? normalizePayload({}),
    select: (raw) => normalizePayload(raw),

    staleTime: staleTimeMs,
    gcTime: 5 * 60 * 1000,
    refetchInterval: refetchIntervalMs,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
    refetchOnReconnect: 'always',
    retry: (failureCount, err: any) => {
      const status = err?.response?.status;
      if (status === 401) return failureCount < 2;
      return failureCount < 3;
    },
  });

  // v5: onSuccess ???data 蹂??媛먯??섏뿬 store 諛섏쁺
  useEffect(() => {
    if (query.data) {
      setPayloadRef.current(query.data);
      // ?붾쾭洹?濡쒓렇(?꾩슂??
      // console.log('[ops] setPayload', {
      // all: query.data.kpis?.all?.length,
      // mh: query.data.moduleHealth?.length,
      // series: query.data.consumerRateSeries?.length,
      // snapshot: query.data.consumerSnapshot?.length,
      // dlq: query.data.dlq?.length,
      // users: query.data.users?.length,
      // reports: query.data.reports?.length,
      // });
    }
  }, [query.data]);

  return query;
}

export function useDlqReprocess() {
  const qc = useQueryClient();
  return useMutation({
    mutationKey: opsKeys.dlqReprocess(),
    mutationFn: ({ topic, dryRun }: { topic: string; dryRun?: boolean }) =>
      reprocessDlq(topic, !!dryRun),
    onSuccess: () => {
      // ??쒕낫???꾩껜 媛깆떊??紐⑺몴硫?猷⑦듃 ??invalidate媛 ??寃ш퀬
      qc.invalidateQueries({ queryKey: opsKeys.root });
    },
  });
}

// ---- Admin: User management queries ----
export function useAdminUserDetailQuery(userId: string, options?: any) {
  return useQuery<CommonResponse<UserProfileResponse>>({
    queryKey: ['admin', 'users', 'detail', userId],
    queryFn: () => getAdminUserDetail(userId),
    enabled: !!userId,
    staleTime: 30_000,
    ...(options as any),
  } as any);
}

export function useAdminUserIdentitiesQuery(userId: string, options?: any) {
  return useQuery<CommonResponse<UserIdentityDto[]>>({
    queryKey: ['admin', 'users', 'identities', userId],
    queryFn: () => getAdminUserIdentities(userId),
    enabled: !!userId,
    staleTime: 30_000,
    ...(options as any),
  } as any);
}

export function useAdminUserEventsQuery(
  userId: string,
  params?: AdminUserEventsParams,
  options?: any,
) {
  return useQuery<CommonResponse<AuthEventResponse[]>>({
    queryKey: ['admin', 'users', 'events', userId, params?.limit, params?.beforeCreatedExclusive, params?.beforeUuidExclusive],
    queryFn: () => getAdminUserEvents(userId, params),
    enabled: !!userId,
    staleTime: 10_000,
    ...(options as any),
  } as any);
}
