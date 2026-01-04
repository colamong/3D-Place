import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UseQueryOptions } from '@tanstack/react-query';

import { clanKeys } from './key';
import {
  createClan,
  updateClan,
  deleteClan,
  getMyClan,
  listPublicClans,
  getClanDetailView,
  joinClan,
  leaveClan,
  changeClanOwner,
  changeMemberRole,
  changeMemberStatus,
  listClanJoinRequests,
  approveClanJoinRequest,
  rejectClanJoinRequest,
} from '@/api/clan';
import type {
  CommonResponse,
  CreateClanRequest,
  UpdateClanProfileRequest,
  ClanDetailView,
  ClanMemberRole,
  MyClanResponse,
} from '@/api/types';

// ======================
// 조회
// ======================

/**
 * 클랜 상세 + 멤버 목록 뷰.
 */
// BFF: GET /api/clans/{clanId}/detail
export function useClanDetailViewQuery(
  clanId: string,
  options?: Omit<
    UseQueryOptions<
      CommonResponse<ClanDetailView>,
      Error,
      CommonResponse<ClanDetailView>,
      ReturnType<typeof clanKeys.detailView>
    >,
    'queryKey' | 'queryFn'
  >,
) {
  return useQuery({
    queryKey: clanKeys.detailView(clanId),
    queryFn: () => getClanDetailView(clanId),
    enabled: !!clanId,
    staleTime: 5_000,
    ...(options as any),
  });
}

/**
 * 내가 속한 단일 클랜 요약.
 */
// BFF: GET /api/clans/me
export function useMyClanQuery(
  options?: Omit<
    UseQueryOptions<
      CommonResponse<MyClanResponse>,
      Error,
      CommonResponse<MyClanResponse>,
      ReturnType<typeof clanKeys.me>
    >,
    'queryKey' | 'queryFn'
  >,
) {
  return useQuery({
    queryKey: clanKeys.me(),
    queryFn: getMyClan,
    staleTime: 5_000,
    ...(options as any),
  });
}

/**
 * 공개 클랜 목록 조회 (검색/정책 필터).
 * - OPEN/APPROVAL 등 정책 필터 가능.
 * - q 키워드 지원.
 */
export function usePublicClansQuery(
  params: {
    q?: string;
    policies?: Array<'OPEN' | 'APPROVAL' | 'INVITE_ONLY'>;
    limit?: number;
    cursor?: string;
  },
  options?: Omit<
    UseQueryOptions<
      CommonResponse<import('@/api/types').PublicClanSummaryResponse[]>,
      Error,
      CommonResponse<import('@/api/types').PublicClanSummaryResponse[]>,
      ReturnType<typeof clanKeys.public>
    >,
    'queryKey' | 'queryFn'
  >,
) {
  return useQuery({
    queryKey: clanKeys.public(params ?? {}),
    queryFn: () => listPublicClans(params ?? {}),
    staleTime: 5_000,
    ...((options as any) ?? {}),
  });
}

// ======================
// 클랜 생성/수정/삭제
// ======================

/**
 * 클랜 생성.
 * - 클라이언트는 name/description/policy 만 보냄
 * - ownerId 는 JWT uid 기준 서버에서 결정
 */
// BFF: POST /api/clans
export function useCreateClanMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateClanRequest) => createClan(body),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: clanKeys.public({}) });
      qc.invalidateQueries({ queryKey: clanKeys.me() });
      if (res?.data?.id) {
        qc.invalidateQueries({ queryKey: clanKeys.detailView(res.data.id) });
      }
    },
  });
}

/**
 * 클랜 프로필 수정 (이름/설명/정책/메타데이터).
 */
// BFF: PATCH /api/clans/{clanId}
export function useUpdateClanMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateClanProfileRequest) => updateClan(clanId, body),
    onSuccess: (res) => {
      // BFF-aligned: detail view + my clan
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
      qc.invalidateQueries({ queryKey: clanKeys.me() });
      // Optional: refresh public listings if present
      qc.invalidateQueries({ queryKey: clanKeys.public({}) });
    },
  });
}

/**
 * 클랜 삭제(해체).
 */
// BFF: DELETE /api/clans/{clanId}
export function useDeleteClanMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => deleteClan(clanId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.me() });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

// ======================
// 멤버십: 가입/탈퇴
// ======================

/**
 * 클랜 가입 (OPEN 정책일 경우 즉시 가입).
 */
// BFF: POST /api/clans/{clanId}/join
export function useJoinClanMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => joinClan(clanId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.me() });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

/**
 * 클랜 탈퇴.
 */
// BFF: POST /api/clans/{clanId}/leave
export function useLeaveClanMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => leaveClan(clanId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.me() });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

// ======================
// 멤버 관리: 오너 변경 / 역할 / 상태
// ======================

/**
 * 클랜 오너 변경.
 */
// BFF: POST /api/clans/{clanId}/owner
export function useChangeOwnerMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targetUserId: string) => changeClanOwner(clanId, targetUserId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

/**
 * 멤버 역할 변경 (MASTER/OFFICER/MEMBER).
 */
// BFF: POST /api/clans/{clanId}/members/{targetUserId}/role
export function useChangeMemberRoleMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: { targetUserId: string; newRole: ClanMemberRole }) =>
      changeMemberRole(clanId, p.targetUserId, p.newRole),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

/**
 * 멤버 상태 변경 (LEFT/KICKED/BANNED 등).
 */
// BFF: POST /api/clans/{clanId}/members/{targetUserId}/status
export function useChangeMemberStatusMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: { targetUserId: string; newStatus: string }) =>
      changeMemberStatus(clanId, p.targetUserId, p.newStatus),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

// 가입 요청 목록 조회
export function useJoinRequestsQuery(
  clanId: string,
  options?: Omit<
    UseQueryOptions<
      CommonResponse<string[]>,
      Error,
      CommonResponse<string[]>,
      ReturnType<typeof clanKeys.joinRequests>
    >,
    'queryKey' | 'queryFn'
  >,
) {
  return useQuery({
    queryKey: clanKeys.joinRequests(clanId),
    queryFn: () => listClanJoinRequests(clanId),
    enabled: !!clanId,
    staleTime: 5_000,
    ...(options as any),
  });
}

// 가입 요청 승인
export function useApproveJoinRequestMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targetUserId: string) =>
      approveClanJoinRequest(clanId, targetUserId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.joinRequests(clanId) });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}

// 가입 요청 거절
export function useRejectJoinRequestMutation(clanId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targetUserId: string) =>
      rejectClanJoinRequest(clanId, targetUserId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: clanKeys.joinRequests(clanId) });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(clanId) });
    },
  });
}
