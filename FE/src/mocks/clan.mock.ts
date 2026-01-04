import { http, HttpResponse } from 'msw';

type Clan = {
  id: string;
  handle: string;
  name: string;
  description?: string | null;
  ownerId: string;
  joinPolicy: 'OPEN' | 'APPROVAL' | 'INVITE_ONLY';
  isPublic: boolean;
  maxMembers: number;
  memberCount: number;
  paintCountTotal: number;
};

type Member = {
  id: string; // membership id
  clanId: string;
  userId: string;
  role: 'MASTER' | 'OFFICER' | 'MEMBER';
  status: 'ACTIVE' | 'INVITED' | 'PENDING' | 'LEFT' | 'KICKED' | 'BANNED';
  joinedAt: string;
  leftAt?: string | null;
  paintCountTotal: number;
};

// In-memory state
const nowIso = () => new Date().toISOString();
const userIdA = '11111111-1111-1111-1111-111111111111'; // match user.mock.ts
const userIdB = '22222222-2222-2222-2222-222222222222';
const userIdC = '33333333-3333-3333-3333-333333333333';
const userIdD = '44444444-4444-4444-4444-444444444444';
const userIdE = '55555555-5555-5555-5555-555555555555';
const userIdF = '66666666-6666-6666-6666-666666666666';

const mockProfiles: Record<string, { nickname: string; handle: string }> = {
  [userIdA]: { nickname: 'SleepyRug', handle: 'sleepy_rug' },
  [userIdB]: { nickname: 'PixelFox', handle: 'pixel_fox' },
  [userIdC]: { nickname: 'NebulaCat', handle: 'nebula_cat' },
  [userIdD]: { nickname: 'CometKoala', handle: 'comet_koala' },
  [userIdE]: { nickname: 'StarDove', handle: 'star_dove' },
  [userIdF]: { nickname: 'NovaBear', handle: 'nova_bear' },
};

const nicknameOf = (userId: string) =>
  mockProfiles[userId]?.nickname ?? `User-${userId.slice(0, 4)}`;
const handleOf = (userId: string) =>
  mockProfiles[userId]?.handle ?? `user_${userId.slice(0, 4)}`;

const clans = new Map<string, Clan>();
const memberships: Member[] = [];
const memberPixelStats = new Map<
  string,
  { today: number; week: number; month: number; all: number }
>();
const inviteTokens = new Map<string, { clanId: string; createdAt: string }>();
// 시나리오 플래그: in_clan(기본), no_clan(미가입), invited(미가입 + 초대 링크 수락 가능)
let mswClanScenario: 'in_clan' | 'no_clan' | 'invited' = 'in_clan';
const joinRequests: Array<{
  id: string;
  clanId: string;
  userId: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';
  message?: string | null;
  reviewerId?: string | null;
  decidedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}> = [];

function currentUserId(
  cookies: Record<string, string | undefined> | undefined,
) {
  const uid = cookies?.['X-MSW-UID'];
  return uid || userIdA;
}

// seed
(() => {
  const c1: Clan = {
    id: crypto.randomUUID(),
    handle: 'sleepy-clan',
    name: 'Sleepy Clan',
    description: '',
    ownerId: userIdA,
    joinPolicy: 'OPEN',
    isPublic: true,
    maxMembers: 50,
    memberCount: 2,
    paintCountTotal: 123456,
  };
  clans.set(c1.id, c1);
  memberships.push(
    {
      id: crypto.randomUUID(),
      clanId: c1.id,
      userId: userIdA,
      role: 'MASTER',
      status: 'ACTIVE',
      joinedAt: nowIso(),
      paintCountTotal: 1000,
    },
    {
      id: crypto.randomUUID(),
      clanId: c1.id,
      userId: userIdB,
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: nowIso(),
      paintCountTotal: 500,
    },
    {
      id: crypto.randomUUID(),
      clanId: c1.id,
      userId: userIdC,
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: nowIso(),
      paintCountTotal: 1000,
    },
    {
      id: crypto.randomUUID(),
      clanId: c1.id,
      userId: userIdD,
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: nowIso(),
      paintCountTotal: 500,
    },
  );
  // seed pixel stats
  memberPixelStats.set(userIdA, { today: 12, week: 78, month: 310, all: 1000 });
  memberPixelStats.set(userIdB, { today: 7, week: 42, month: 180, all: 500 });
  // c1에 대한 가입 요청(PENDING) 더미 생성: 아직 멤버가 아닌 사용자로 생성
  joinRequests.push(
    {
      id: crypto.randomUUID(),
      clanId: c1.id,
      userId: userIdE,
      status: 'PENDING',
      message: 'Hi, I would like to join!',
      reviewerId: null,
      decidedAt: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    },
    {
      id: crypto.randomUUID(),
      clanId: c1.id,
      userId: userIdF,
      status: 'PENDING',
      message: 'Let me in please',
      reviewerId: null,
      decidedAt: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    },
  );
  // 추가 시드: OPEN 5개, APPROVAL 5개
  const openNames = [
    'Open Tigers',
    'Open Falcons',
    'Open Wolves',
    'Open Bears',
    'Open Sharks',
  ];
  const approvalNames = [
    'Approval Owls',
    'Approval Lions',
    'Approval Eagles',
    'Approval Panthers',
    'Approval Dragons',
  ];
  const makeClan = (
    name: string,
    policy: 'OPEN' | 'APPROVAL',
    members: number,
    pixels: number,
  ): Clan => ({
    id: crypto.randomUUID(),
    handle: name.toLowerCase().replace(/\s+/g, '-').slice(0, 24),
    name,
    description: '',
    ownerId: userIdA,
    joinPolicy: policy,
    isPublic: true,
    maxMembers: 50,
    memberCount: members,
    paintCountTotal: pixels,
  });
  const seedBase = 10_000;
  openNames.forEach((n, i) => {
    const c = makeClan(n, 'OPEN', 5 + i, seedBase + i * 1111);
    clans.set(c.id, c);
  });
  approvalNames.forEach((n, i) => {
    const c = makeClan(
      n,
      'APPROVAL',
      3 + i,
      Math.floor(seedBase / 2) + i * 1007,
    );
    clans.set(c.id, c);
  });
})();

function ok<T>(path: string, data: T) {
  return HttpResponse.json({
    timestamp: nowIso(),
    status: 200,
    code: 'SUCCESS',
    message: 'OK',
    path,
    traceId: 'mock',
    data,
  });
}

// ===== Invite token helpers (typed, base64url) =====
type InviteTokenPayload = {
  clanId: string;
  name?: string;
  memberCount?: number;
  iat: number; // issued at (ms)
  jti: string; // random id
};

function toBase64Url(json: string): string {
  const bytes = new TextEncoder().encode(json);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function fromBase64Url(b64url: string): string {
  const pad = (4 - (b64url.length % 4)) % 4;
  const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat(pad);
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}
function encodeToken(payload: InviteTokenPayload): string {
  return toBase64Url(JSON.stringify(payload));
}
function decodeToken(token: string): InviteTokenPayload | null {
  try {
    return JSON.parse(fromBase64Url(token)) as InviteTokenPayload;
  } catch {
    return null;
  }
}

export const clanHandlers = [
  // ======================
  // 조회
  // ======================

  /**
   * 클랜 상세 + 멤버 목록 뷰.
   */
  // BFF: GET /api/clans/{clanId}/detail
  http.get('*/api/clans/:id/detail', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const c = clans.get(id);
    if (!c) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    const members = memberships
      .filter((m) => m.clanId === id)
      .map((m, idx) => ({
        id: m.id,
        userId: m.userId,
        nickname: nicknameOf(m.userId),
        nicknameHandle: handleOf(m.userId),
        role: m.role,
        status: m.status,
        paintCountTotal: m.paintCountTotal,
        joinedAt: m.joinedAt,
        leftAt: m.leftAt ?? null,
      }));
    const detail = {
      id: c.id,
      name: c.name,
      description: c.description ?? '',
      ownerNickname: nicknameOf(c.ownerId),
      ownerNicknameHandle: handleOf(c.ownerId),
      joinPolicy: c.joinPolicy,
      isPublic: c.isPublic,
      memberCount: c.memberCount,
      paintCountTotal: c.paintCountTotal,
      metadata: {},
      createdAt: nowIso(),
      updatedAt: nowIso(),
      members,
    };
    return ok(path, detail as any);
  }),

  /**
   * 내가 속한 단일 클랜 요약.
   */
  // BFF: GET /api/clans/me
  http.get('*/api/clans/me', ({ request, cookies }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const uid = currentUserId(cookies as any);
    if (mswClanScenario === 'no_clan' || mswClanScenario === 'invited') {
      return ok(path, null as any);
    }
    const m = memberships.find(
      (x) => x.userId === uid && x.status === 'ACTIVE',
    );
    if (!m) return ok(path, null as any);
    const c = clans.get(m.clanId)!;
    return ok(path, {
      id: c.id,
      name: c.name,
      description: c.description ?? '',
      joinPolicy: c.joinPolicy,
      isPublic: c.isPublic,
      memberCount: c.memberCount,
      paintCountTotal: c.paintCountTotal,
    });
  }),

  // ======================
  // 클랜 생성/수정/삭제
  // ======================

  /**
   * 클랜 생성.
   * - 클라이언트는 name/description/policy 만 보냄
   * - ownerId 는 JWT uid 기준 서버에서 결정
   */
  // BFF: POST /api/clans
  http.post('*/api/clans', async ({ request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const body = (await request.json()) as {
      name: string;
      description: string;
      policy: 'OPEN' | 'APPROVAL' | 'INVITE_ONLY';
      handle?: string;
    };
    const id = crypto.randomUUID();
    const handle =
      body.handle || body.name.toLowerCase().replace(/\s+/g, '_').slice(0, 20);
    const ownerId = userIdA;
    const c: Clan = {
      id,
      handle,
      name: body.name,
      description: body.description ?? '',
      ownerId,
      joinPolicy: body.policy,
      isPublic: true,
      maxMembers: 50,
      memberCount: 1,
      paintCountTotal: 0,
    };
    clans.set(id, c);
    memberships.push({
      id: crypto.randomUUID(),
      clanId: id,
      userId: ownerId,
      role: 'MASTER',
      status: 'ACTIVE',
      joinedAt: nowIso(),
      paintCountTotal: 0,
    });
    const detail = {
      id: c.id,
      name: c.name,
      description: c.description ?? '',
      ownerId: c.ownerId,
      joinPolicy: c.joinPolicy,
      isPublic: c.isPublic,
      memberCount: c.memberCount,
      paintCountTotal: c.paintCountTotal,
      metadata: {},
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    return ok(path, detail as any);
  }),

  /**
   * 클랜 프로필 수정 (이름/설명/정책/메타데이터).
   */
  // BFF: PATCH /api/clans/{clanId}
  http.patch('*/api/clans/:id', async ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const body = (await request.json()) as {
      newName?: string;
      newDescription?: string | null;
      metadataPatch?: any;
      joinPolicy?: 'OPEN' | 'APPROVAL' | 'INVITE_ONLY';
    };
    const c = clans.get(id);
    if (!c) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    if (body.newName) c.name = body.newName;
    if (body.newDescription !== undefined)
      c.description = body.newDescription ?? '';
    if (body.joinPolicy) c.joinPolicy = body.joinPolicy;
    const next = { ...c } as Clan;
    clans.set(id, next);
    const detail = {
      id: next.id,
      name: next.name,
      description: next.description ?? '',
      ownerId: next.ownerId,
      joinPolicy: next.joinPolicy,
      isPublic: next.isPublic,
      memberCount: next.memberCount,
      paintCountTotal: next.paintCountTotal,
      metadata: {},
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    return ok(path, detail as any);
  }),

  /**
   * 클랜 삭제(해체).
   */
  // BFF: DELETE /api/clans/{clanId}
  http.delete('*/api/clans/:id', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    clans.delete(id);
    for (let i = memberships.length - 1; i >= 0; i--) {
      if (memberships[i].clanId === id) memberships.splice(i, 1);
    }
    return ok(path, null as any);
  }),

  // ======================
  // 멤버십: 가입/탈퇴
  // ======================

  /**
   * 클랜 가입 (OPEN 정책일 경우 즉시 가입).
   */
  // BFF: POST /api/clans/{clanId}/join
  http.post('*/api/clans/:id/join', ({ params, request, cookies }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const uid = currentUserId(cookies as any);
    // simple: userIdA joins if not already active
    const exists = memberships.find(
      (m) => m.clanId === id && m.userId === uid && m.status === 'ACTIVE',
    );
    if (!exists) {
      memberships.push({
        id: crypto.randomUUID(),
        clanId: id,
        userId: uid,
        role: 'MEMBER',
        status: 'ACTIVE',
        joinedAt: nowIso(),
        paintCountTotal: 0,
      });
      const c = clans.get(id);
      if (c) c.memberCount += 1;
    }
    // 일관성 유지를 위해 시나리오도 갱신
    mswClanScenario = 'in_clan';
    return ok(path, null as any);
  }),

  /**
   * 클랜 탈퇴.
   */
  // BFF: POST /api/clans/{clanId}/leave
  http.post('*/api/clans/:id/leave', ({ params, request, cookies }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const uid = currentUserId(cookies as any);
    const idx = memberships.findIndex(
      (m) => m.clanId === id && m.userId === uid && m.status === 'ACTIVE',
    );
    if (idx >= 0) memberships.splice(idx, 1);
    const c = clans.get(id);
    if (c && c.memberCount > 0) c.memberCount -= 1;
    // 더 이상 어떤 클랜에도 속해있지 않다면 시나리오 갱신
    const stillIn = memberships.some(
      (m) => m.userId === uid && m.status === 'ACTIVE',
    );
    if (!stillIn) mswClanScenario = 'no_clan';
    return ok(path, null as any);
  }),

  // ======================
  // 멤버 관리: 오너 변경 / 역할 / 상태
  // ======================

  /**
   * 클랜 오너 변경.
   */
  // BFF: POST /api/clans/{clanId}/owner
  http.post('*/api/clans/:id/owner', async ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const body = (await request.json().catch(() => ({}) as any)) as {
      targetUserId?: string;
    };
    const c = clans.get(id);
    if (!c) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    if (body?.targetUserId) {
      // demote previous master -> MEMBER (요청에 따라 OFFICER가 아닌 MEMBER로 강등)
      const prev = memberships.find(
        (m) => m.clanId === id && m.userId === c.ownerId,
      );
      if (prev) prev.role = 'MEMBER';
      // promote target
      const tgt = memberships.find(
        (m) => m.clanId === id && m.userId === body.targetUserId,
      );
      if (tgt) tgt.role = 'MASTER';
      c.ownerId = body.targetUserId;
    }
    return ok(path, null as any);
  }),

  /**
   * 멤버 역할 변경 (MASTER/OFFICER/MEMBER).
   */
  // BFF: POST /api/clans/{clanId}/members/{targetUserId}/role
  http.post(
    '*/api/clans/:id/members/:userId/role',
    async ({ params, request }) => {
      const url = new URL(request.url);
      const path = url.pathname;
      const id = String(params.id);
      const userId = String(params.userId);
      const body = (await request.json().catch(() => ({}) as any)) as {
        newRole?: string;
      };
      const m = memberships.find((x) => x.clanId === id && x.userId === userId);
      if (!m)
        return HttpResponse.json({ message: 'Not found' }, { status: 404 });
      if (body?.newRole) m.role = body.newRole.toUpperCase() as any;
      return ok(path, null as any);
    },
  ),

  /**
   * 멤버 상태 변경 (LEFT/KICKED/BANNED 등).
   */
  // BFF: POST /api/clans/{clanId}/members/{targetUserId}/status
  http.post(
    '*/api/clans/:id/members/:userId/status',
    async ({ params, request }) => {
      const url = new URL(request.url);
      const path = url.pathname;
      const id = String(params.id);
      const userId = String(params.userId);
      const body = (await request.json().catch(() => ({}) as any)) as {
        newStatus?: string;
      };
      const m = memberships.find((x) => x.clanId === id && x.userId === userId);
      if (!m)
        return HttpResponse.json({ message: 'Not found' }, { status: 404 });
      const prev = m.status;
      const next = (body?.newStatus || '').toUpperCase();
      if (next) m.status = next as any;
      // active count adjust
      const c = clans.get(id);
      if (c) {
        const wasActive = prev === 'ACTIVE';
        const nowActive = m.status === 'ACTIVE';
        if (wasActive && !nowActive && c.memberCount > 0) c.memberCount -= 1;
        if (!wasActive && nowActive) c.memberCount += 1;
      }
      return ok(path, null as any);
    },
  ),

  // 아래는 전부 BFF 미구현 항목들

  // BFF 미구현(프론트 임의 구현): 가입 요청 생성

  // list public clans
  http.get('*/api/clans/public', ({ request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const policies = [
      ...url.searchParams.getAll('policies'),
      ...url.searchParams.getAll('policies[]'),
    ];
    const policySet = new Set(
      policies.length > 0
        ? policies.map((p) => p.toUpperCase())
        : ['OPEN', 'APPROVAL'],
    );
    const rows = Array.from(clans.values())
      .filter((c) => c.isPublic)
      .filter((c) => policySet.has(c.joinPolicy))
      .filter(
        (c) => !q || c.name.toLowerCase().includes(q) || c.handle.includes(q),
      )
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((c) => ({
        id: c.id,
        name: c.name,
        joinPolicy: c.joinPolicy,
        memberCount: c.memberCount,
        paintCountTotal: c.paintCountTotal,
      }));
    return ok(path, rows);
  }),

  // get by id
  http.get('*/api/clans/:id', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const c = clans.get(id);
    if (!c) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    return ok(path, c);
  }),

  // get by handle
  http.get('*/api/clans/handle/:handle', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const handle = String(params.handle);
    const c = Array.from(clans.values()).find((x) => x.handle === handle);
    if (!c) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    return ok(path, c);
  }),

  // list by owner
  http.get('*/api/clans/owner/:ownerId', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const ownerId = String(params.ownerId);
    const rows = Array.from(clans.values())
      .filter((c) => c.ownerId === ownerId)
      .map((c) => ({
        id: c.id,
        handle: c.handle,
        name: c.name,
        isPublic: c.isPublic,
        memberCount: c.memberCount,
        paintCountTotal: c.paintCountTotal,
      }));
    return ok(path, rows);
  }),

  // list by member
  http.get('*/api/clans/member/:userId', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const userId = String(params.userId);
    const membershipsActive = memberships.filter(
      (m) => m.userId === userId && m.status === 'ACTIVE',
    );
    const rows = membershipsActive
      .map((m) => clans.get(m.clanId))
      .filter(Boolean)
      .map((c) => ({
        id: (c as Clan).id,
        handle: (c as Clan).handle,
        name: (c as Clan).name,
        isPublic: (c as Clan).isPublic,
        memberCount: (c as Clan).memberCount,
        paintCountTotal: (c as Clan).paintCountTotal,
      }));
    return ok(path, rows);
  }),

  // join request (simple mock)
  http.post('*/api/clans/:id/join-requests', async ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const body = (await request.json()) as { message?: string };
    const jr = {
      id: crypto.randomUUID(),
      clanId: id,
      userId: userIdB,
      status: 'PENDING' as const,
      message: body?.message ?? null,
      reviewerId: null,
      decidedAt: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    joinRequests.push(jr);
    return ok(path, jr);
  }),

  // join requests list (IDs only)
  http.get('*/api/clans/:id/join-requests', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const rows = joinRequests
      .filter((r) => r.clanId === id && r.status === 'PENDING')
      .map((r) => r.userId);
    return ok(path, rows as any);
  }),

  // approve join request
  http.post(
    '*/api/clans/:id/join-requests/:targetUserId/approve',
    ({ params, request }) => {
      const url = new URL(request.url);
      const path = url.pathname;
      const id = String(params.id);
      const targetUserId = String(params.targetUserId);
      const jr = joinRequests.find(
        (r) => r.userId === targetUserId && r.clanId === id,
      );
      if (!jr)
        return HttpResponse.json({ message: 'Not found' }, { status: 404 });
      if (jr.status !== 'PENDING') return ok(path, { ok: true } as any);
      jr.status = 'APPROVED';
      jr.reviewerId = userIdA;
      jr.decidedAt = nowIso();
      jr.updatedAt = nowIso();
      // add membership for requester
      const exists = memberships.find(
        (m) =>
          m.clanId === id && m.userId === jr.userId && m.status === 'ACTIVE',
      );
      if (!exists) {
        memberships.push({
          id: crypto.randomUUID(),
          clanId: id,
          userId: jr.userId,
          role: 'MEMBER',
          status: 'ACTIVE',
          joinedAt: nowIso(),
          paintCountTotal: 0,
        });
        const c = clans.get(id);
        if (c) c.memberCount += 1;
      }
      return ok(path, { ok: true } as any);
    },
  ),

  // reject join request
  http.post(
    '*/api/clans/:id/join-requests/:targetUserId/reject',
    ({ params, request }) => {
      const url = new URL(request.url);
      const path = url.pathname;
      const id = String(params.id);
      const targetUserId = String(params.targetUserId);
      const jr = joinRequests.find(
        (r) => r.userId === targetUserId && r.clanId === id,
      );
      if (!jr)
        return HttpResponse.json({ message: 'Not found' }, { status: 404 });
      if (jr.status !== 'PENDING') return ok(path, { ok: true } as any);
      jr.status = 'REJECTED';
      jr.reviewerId = userIdA;
      jr.decidedAt = nowIso();
      jr.updatedAt = nowIso();
      return ok(path, { ok: true } as any);
    },
  ),

  // invite user (creates membership with INVITED)
  http.post('*/api/clans/:id/invites', async ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const body = (await request.json()) as { userId: string };
    const m: Member = {
      id: crypto.randomUUID(),
      clanId: id,
      userId: body.userId,
      role: 'MEMBER',
      status: 'INVITED',
      joinedAt: nowIso(),
      paintCountTotal: 0,
    };
    memberships.push(m);
    return ok(path, m as any);
  }),

  // accept / decline invite
  http.post(
    '*/api/clans/:id/invites/accept',
    ({ params, request, cookies }) => {
      const url = new URL(request.url);
      const path = url.pathname;
      const id = String(params.id);
      const uid = currentUserId(cookies as any);
      const m = memberships.find(
        (x) => x.clanId === id && x.userId === uid && x.status === 'INVITED',
      );
      if (m) {
        m.status = 'ACTIVE';
        const c = clans.get(id);
        if (c) c.memberCount += 1;
        return ok(path, m as any);
      }
      return HttpResponse.json(
        { message: 'No pending invite' },
        { status: 400 },
      );
    },
  ),
  http.post(
    '*/api/clans/:id/invites/decline',
    ({ params, request, cookies }) => {
      const url = new URL(request.url);
      const path = url.pathname;
      const id = String(params.id);
      const uid = currentUserId(cookies as any);
      const idx = memberships.findIndex(
        (x) => x.clanId === id && x.userId === uid && x.status === 'INVITED',
      );
      if (idx >= 0) memberships.splice(idx, 1);
      return ok(path, { ok: true });
    },
  ),

  // invite preference (user-level setting)
  http.put('*/api/clans/invites/preference', async ({ request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const body = (await request.json()) as { acceptInvites: boolean };
    return ok(path, {
      acceptInvites: !!body.acceptInvites,
      updatedAt: nowIso(),
    });
  }),

  // invite link create/resolve/consume

  http.post('*/api/clans/:id/invites/link', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const c = clans.get(id);
    const payload = {
      clanId: id,
      name: c?.name ?? 'Unknown clan',
      memberCount: c?.memberCount ?? 0,
      iat: Date.now(),
      jti: crypto.randomUUID(),
    };
    const token = encodeToken(payload);
    // 여전히 맵에도 저장(동일 세션에서 즉시 확인 가능)
    inviteTokens.set(token, { clanId: id, createdAt: nowIso() });
    const link = `${window.location.origin}/join?token=${encodeURIComponent(
      token,
    )}`;
    return ok(path, { token, url: link });
  }),
  http.get('*/api/clans/invites/resolve', ({ request, cookies }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const token = String(url.searchParams.get('token') || '');
    const payload = decodeToken(token);
    let clanId = payload?.clanId as string | undefined;
    if (!clanId) {
      const found = inviteTokens.get(token);
      clanId = found?.clanId;
    }
    if (!clanId)
      return HttpResponse.json({ message: 'Invalid token' }, { status: 404 });
    const c = clans.get(clanId);
    const authenticated = Boolean(cookies?.AUTHSESSION);
    let joinableHint: 'OK' | 'REQUIRES_LOGIN' | 'ALREADY_IN_CLAN' = 'OK';
    if (!authenticated) joinableHint = 'REQUIRES_LOGIN';
    else {
      // 실제 멤버십 기준으로도 이미 클랜이 있으면 막는다
      const uid = currentUserId(cookies as any);
      const hasClan = memberships.some(
        (m) => m.userId === uid && m.status === 'ACTIVE',
      );
      if (hasClan) joinableHint = 'ALREADY_IN_CLAN';
    }
    return ok(path, {
      token,
      clan: c
        ? {
            id: c.id,
            handle: c.handle,
            name: c.name,
            memberCount: c.memberCount,
          }
        : {
            id: clanId,
            handle: (payload?.name || 'clan')
              .toLowerCase()
              .replace(/\s+/g, '-')
              .slice(0, 24),
            name: payload?.name || 'Unknown clan',
            memberCount: payload?.memberCount ?? 0,
          },
      joinableHint,
    });
  }),
  http.post('*/api/clans/invites/consume', async ({ request, cookies }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const body = (await request.json()) as { token: string };
    const payload = decodeToken(body.token);
    const id =
      (payload?.clanId as string) || inviteTokens.get(body.token)?.clanId;
    if (!id)
      return HttpResponse.json({ message: 'Invalid token' }, { status: 404 });
    const uid = currentUserId(cookies as any);
    const existing = memberships.find(
      (m) => m.userId === uid && m.status === 'ACTIVE',
    );
    if (existing && existing.clanId !== id) {
      return HttpResponse.json(
        { message: 'Already in another clan' },
        { status: 409 },
      );
    }
    // 클랜이 없다면 토큰 정보로 생성
    if (!clans.has(id)) {
      const name = payload?.name || 'Invited Clan';
      const handle = name.toLowerCase().replace(/\s+/g, '-').slice(0, 24);
      clans.set(id, {
        id,
        handle,
        name,
        description: '',
        ownerId: currentUserId(cookies as any),
        joinPolicy: 'OPEN',
        isPublic: true,
        maxMembers: 50,
        memberCount: 0,
        paintCountTotal: 0,
      });
    }
    const m: Member = {
      id: crypto.randomUUID(),
      clanId: id,
      userId: uid,
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: nowIso(),
      paintCountTotal: 0,
    };
    memberships.push(m);
    const c = clans.get(id);
    if (c) c.memberCount += 1;
    inviteTokens.delete(body.token);
    mswClanScenario = 'in_clan';
    return ok(path, { clanId: id, userId: uid });
  }),

  // members list / ban / unban / kick
  http.get('*/api/clans/:id/members', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const rows = memberships
      .filter((m) => m.clanId === id)
      .map((m) => ({
        id: m.id,
        clanId: m.clanId,
        userId: m.userId,
        role: m.role,
        status: m.status,
        paintCountTotal: m.paintCountTotal,
        joinedAt: m.joinedAt,
        leftAt: m.leftAt ?? null,
        createdAt: m.joinedAt,
        updatedAt: nowIso(),
      }));
    return ok(path, rows);
  }),

  // ===== Mock control endpoints (DEV only) =====
  http.get('*/api/_mock/clan/scenario', () => {
    return ok('/api/_mock/clan/scenario', { mode: mswClanScenario });
  }),
  http.post('*/api/_mock/clan/scenario', async ({ request, cookies }) => {
    const body = (await request.json().catch(() => ({}))) as {
      mode?: 'in_clan' | 'no_clan' | 'invited';
    };
    const mode = body?.mode || 'in_clan';
    mswClanScenario = mode;
    // 상태 정합성 최소 보장: no_clan이면 userIdA의 ACTIVE 멤버십 제거
    if (mode === 'no_clan') {
      const uid = currentUserId(cookies as any);
      for (let i = memberships.length - 1; i >= 0; i--) {
        if (
          memberships[i].userId === uid &&
          memberships[i].status === 'ACTIVE'
        ) {
          const c = clans.get(memberships[i].clanId);
          if (c && c.memberCount > 0) c.memberCount -= 1;
          memberships.splice(i, 1);
        }
      }
    }
    return ok('/api/_mock/clan/scenario', { mode });
  }),

  // ban/unban/kick 은 status 엔드포인트로 처리합니다.
  // leaderboard by members
  http.get('*/api/clans/:id/leaderboard', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const id = String(params.id);
    const period = (url.searchParams.get('period') || 'today') as
      | 'today'
      | 'week'
      | 'month'
      | 'all';
    const rows = memberships
      .filter((m) => m.clanId === id && m.status === 'ACTIVE')
      .map((m) => {
        const stats = memberPixelStats.get(m.userId) || {
          today: 0,
          week: 0,
          month: 0,
          all: 0,
        };
        const pixels = stats[period];
        // nickname mock: userIdA => SleepyRug, userIdB => PixelFox
        const nickname =
          m.userId === userIdA
            ? 'SleepyRug'
            : m.userId === userIdB
              ? 'PixelFox'
              : m.userId.slice(0, 8);
        return { userId: m.userId, nickname, pixels };
      })
      .sort((a, b) => b.pixels - a.pixels)
      .map((r, i) => ({ rank: i + 1, ...r }));
    return ok(path, rows);
  }),
];
