import { api } from './instance';
import type {
  CommonResponse,
  CreateClanRequest,
  UpdateClanProfileRequest,
  ClanSummaryResponse,
  ClanDetailResponse,
  ClanDetailView,
  ClanMemberRole,
  ClanInviteTicketResponse,
  PublicClanSummaryResponse,
  MyClanResponse,
} from './types';

const base = {
  root: '/clans',
} as const;

// ======================
// 조회
// ======================

/**
 * 클랜 상세 + 멤버 목록 뷰.
 */
// BFF: GET /api/clans/{clanId}/detail
export async function getClanDetailView(
  clanId: string,
): Promise<CommonResponse<ClanDetailView>> {
  const { data } = await api.get(
    `${base.root}/${encodeURIComponent(clanId)}/detail`,
  );
  return data as CommonResponse<ClanDetailView>;
}

/**
 * 내가 속한 단일 클랜 요약.
 */
// BFF: GET /api/clans/me
export async function getMyClan(): Promise<CommonResponse<MyClanResponse>> {
  const { data } = await api.get(`${base.root}/me`);
  return data as CommonResponse<MyClanResponse>;
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
export async function createClan(
  body: CreateClanRequest,
): Promise<CommonResponse<ClanDetailResponse>> {
  // 이름만으로 생성 가능한 정책을 반영(기타 필드는 서버 기본값/자동생성)
  const { data } = await api.post(base.root, body);
  return data as CommonResponse<ClanDetailResponse>;
}

/**
 * 클랜 프로필 수정 (이름/설명/정책/메타데이터).
 */
// BFF: PATCH /api/clans/{clanId}
export async function updateClan(
  clanId: string,
  body: UpdateClanProfileRequest,
): Promise<CommonResponse<ClanDetailResponse>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}`,
    body,
  );
  return data as CommonResponse<ClanDetailResponse>;
}

/**
 * 클랜 삭제(해체).
 */
// BFF: DELETE /api/clans/{clanId}
export async function deleteClan(
  clanId: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.delete(
    `${base.root}/${encodeURIComponent(clanId)}`,
  );
  return data as CommonResponse<void>;
}

// ======================
// 멤버십: 가입/탈퇴
// ======================

/**
 * 클랜 가입 (OPEN 정책일 경우 즉시 가입).
 */
// BFF: POST /api/clans/{clanId}/join
export async function joinClan(clanId: string): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/join`,
  );
  return data as CommonResponse<void>;
}

/**
 * 가입 신청 취소.
 */
// BFF: POST /api/clans/{clanId}/join-requests/cancel
export async function cancelJoinRequest(
  clanId: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/join-requests/cancel`,
  );
  return data as CommonResponse<void>;
}

/**
 * 클랜 탈퇴.
 */
// BFF: POST /api/clans/{clanId}/leave
export async function leaveClan(clanId: string): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/leave`,
  );
  return data as CommonResponse<void>;
}

/**
 * 클랜 가입 요청(PENDING) 목록 - userId 리스트.
 * (유저 정보 조합은 추후 userClient로 확장)
 */
// BFF: GET /api/clans/{clanId}/join-requests
export async function listClanJoinRequests(
  clanId: string,
  params?: { page?: number; size?: number },
): Promise<CommonResponse<string[]>> {
  const { data } = await api.get(
    `${base.root}/${encodeURIComponent(clanId)}/join-requests`,
    {
      params: {
        page: params?.page,
        size: params?.size,
      },
    },
  );
  return data as CommonResponse<string[]>;
}

/**
 * 클랜 가입 요청 승인.
 */
// BFF: POST
export async function approveClanJoinRequest(
  clanId: string,
  targetUserId: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/join-requests/${encodeURIComponent(
      targetUserId,
    )}/approve`,
  );
  return data as CommonResponse<void>;
}

/**
 * 클랜 가입 요청 거절.
 */
// BFF: POST
export async function rejectClanJoinRequest(
  clanId: string,
  targetUserId: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/join-requests/${encodeURIComponent(
      targetUserId,
    )}/reject`,
  );
  return data as CommonResponse<void>;
}

// ======================
// 초대 링크: 생성 / 수락
// ======================

/**
 * 클랜 초대 링크 생성.
 * - body.maxUses: 사용 가능 횟수 (null 또는 0이면 무제한)
 */
// BFF: POST /api/clans/{clanId}/invites
export async function issueClanInviteTicket(
  clanId: string,
  maxUses?: number,
): Promise<CommonResponse<ClanInviteTicketResponse>> {
  const body: { maxUses?: number } = {};
  if (typeof maxUses === 'number') body.maxUses = maxUses;
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/invites`,
    body,
  );
  return data as CommonResponse<ClanInviteTicketResponse>;
}

/**
 * 초대 코드로 클랜 가입.
 * - 클라이언트는 초대 코드만 보내고, userId는 JWT에서 가져옴.
 */
// BFF: POST /api/clans/join-by-invite
export async function joinClanByInvite(
  inviteCode: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(`${base.root}/join-by-invite`, {
    inviteCode,
  });
  return data as CommonResponse<void>;
}

// ======================
// 멤버 관리: 오너 변경 / 역할 / 상태
// ======================

/**
 * 클랜 오너 변경.
 */
// BFF: POST /api/clans/{clanId}/owner
export async function changeClanOwner(
  clanId: string,
  targetUserId: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/owner`,
    { targetUserId },
  );
  return data as CommonResponse<void>;
}

/**
 * 멤버 역할 변경 (MASTER/OFFICER/MEMBER).
 */
// BFF: POST /api/clans/{clanId}/members/{targetUserId}/role
export async function changeMemberRole(
  clanId: string,
  targetUserId: string,
  newRole: ClanMemberRole,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/members/${encodeURIComponent(
      targetUserId,
    )}/role`,
    { newRole },
  );
  return data as CommonResponse<void>;
}

/**
 * 멤버 상태 변경 (LEFT/KICKED/BANNED 등).
 */
// BFF: POST /api/clans/{clanId}/members/{targetUserId}/status
export async function changeMemberStatus(
  clanId: string,
  targetUserId: string,
  newStatus: string,
): Promise<CommonResponse<void>> {
  const { data } = await api.post(
    `${base.root}/${encodeURIComponent(clanId)}/members/${encodeURIComponent(
      targetUserId,
    )}/status`,
    { newStatus },
  );
  return data as CommonResponse<void>;
}

/**
 * 공개 클랜 목록 조회 (검색/정책 필터 지원).
 * - 예상 BFF 엔드포인트: GET /api/clans/public
 * - params:
 *   - q?: 키워드 검색
 *   - policies?: ['OPEN','APPROVAL'] 등 조인 정책 필터
 *   - limit?: 페이지 크기
 *   - cursor?: 커서 페이징 토큰
 */
export async function listPublicClans(params: {
  q?: string;
  policies?: Array<'OPEN' | 'APPROVAL' | 'INVITE_ONLY'>;
  limit?: number;
  cursor?: string;
}): Promise<CommonResponse<PublicClanSummaryResponse[]>> {
  const { data } = await api.get(`${base.root}/public`, {
    params: {
      q: params.q,
      policies: params.policies,
      limit: params.limit,
      cursor: params.cursor,
    },
  });
  return data as CommonResponse<PublicClanSummaryResponse[]>;
}
