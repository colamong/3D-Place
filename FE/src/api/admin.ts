import type { ModuleId, OpsDashboardPayload } from '@/types/admin';
import { adminApi } from './instance';
import type {
  CommonResponse,
  UserProfileResponse,
  UserIdentityDto,
  AuthEventResponse,
  AdminUserEventsParams,
} from './types';

// Admin: get user detail
// @GetMapping("/{userId}")
export async function getAdminUserDetail(
  userId: string,
): Promise<CommonResponse<UserProfileResponse>> {
  const { data } = await adminApi.get(`/users/${encodeURIComponent(userId)}`);
  return data as CommonResponse<UserProfileResponse>;
}

// Admin: get user identities
// @GetMapping("/{userId}/identities")
export async function getAdminUserIdentities(
  userId: string,
): Promise<CommonResponse<UserIdentityDto[]>> {
  const { data } = await adminApi.get(
    `/users/${encodeURIComponent(userId)}/identities`,
  );
  return data as CommonResponse<UserIdentityDto[]>;
}

// Admin: get auth events
// @GetMapping("/{userId}/events")
export async function getAdminUserEvents(
  userId: string,
  params?: AdminUserEventsParams,
): Promise<CommonResponse<AuthEventResponse[]>> {
  const { data } = await adminApi.get(
    `/users/${encodeURIComponent(userId)}/events`,
    { params },
  );
  return data as CommonResponse<AuthEventResponse[]>;
}

// ---- Ops dashboard (kept for admin UI; may rely on MSW or future BFF endpoints) ----
export async function fetchOpsDashboard(
  scope: ModuleId,
  signal?: AbortSignal,
): Promise<OpsDashboardPayload> {
  const { data } = await adminApi.get<OpsDashboardPayload>('/dashboard', {
    params: { scope },
    signal,
  });
  return data;
}

export async function reprocessDlq(
  topic: string,
  dryRun = false,
): Promise<{ ok: boolean; processed: number }> {
  const { data } = await adminApi.post('/dlq/reprocess', { topic, dryRun });
  return data;
}
