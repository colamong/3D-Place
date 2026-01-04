import { http, HttpResponse } from 'msw';

// In-memory mock user state so that POST /profile persists to GET /me
const MOCK_ID = '11111111-1111-1111-1111-111111111111';
let mockUser = (() => {
  const now = new Date().toISOString();
  return {
    userId: MOCK_ID,
    email: 'user@example.com',
    emailVerified: true,
    nickname: 'SleepyRug',
    nicknameSeq: 11981275,
    nicknameHandle: 'sleepy_rug',
    isActive: true,
    loginCount: 290,
    metadata: { theme: 'dark' } as any,
    lastLoginAt: now,
    blockedAt: null as any,
    blockedReason: null as any,
    createdAt: now,
    updatedAt: now,
    roles: ['user'] as string[],
    identities: [] as any[],
  };
})();

export const userHandlers = [
  // 내 프로필 조회 (CommonResponse<UserProfileResponse>) - BFF
  http.get('*/api/users/me', ({ request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const now = new Date().toISOString();
    return HttpResponse.json(
      {
        timestamp: now,
        status: 200,
        code: 'SUCCESS',
        message: '요청이 성공적으로 처리되었습니다',
        path,
        traceId: 'mock-trace-id',
        data: mockUser,
      },
      { status: 200 },
    );
  }),

  // 관리자용 유저 상세 - Admin BFF
  http.get('*/api/admin/users/:userId', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const now = new Date().toISOString();
    const userId = String(params.userId);
    const payload = userId === MOCK_ID ? mockUser : { ...mockUser, userId };
    return HttpResponse.json({
      timestamp: now,
      status: 200,
      code: 'SUCCESS',
      message: 'OK',
      path,
      traceId: 'mock',
      data: payload,
    });
  }),

  // 공개 프로필(핸들) - BFF
  http.get('*/api/users/profile/:nicknameHandle', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const now = new Date().toISOString();
    const handle = String(params.nicknameHandle);
    const payload = {
      userId: MOCK_ID,
      nickname: 'SleepyRug',
      nicknameSeq: 11981275,
      nicknameHandle: handle || 'sleepy_rug',
      metadata: { bio: 'hello' } as any,
      createdAt: now,
    };
    return HttpResponse.json({
      timestamp: now,
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공적으로 처리되었습니다',
      path,
      traceId: 'mock',
      data: payload,
    });
  }),

  // 아이덴티티 목록 - Admin BFF
  http.get('*/api/admin/users/:userId/identities', ({ params, request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const now = new Date().toISOString();
    const uid = String(params.userId || MOCK_ID);
    const data = [
      {
        id: crypto.randomUUID(),
        userId: uid,
        provider: 'GOOGLE',
        providerTenant: 'default',
        providerSub: '1234567890',
        emailAtProvider: 'user@example.com',
        displayNameAtProvider: 'Sleepy Rug',
        lastSyncAt: now,
        createdAt: now,
        updatedAt: now,
        deletedAt: null as any,
      },
    ];
    return HttpResponse.json({
      timestamp: now,
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공적으로 처리되었습니다',
      path,
      traceId: 'mock',
      data,
    });
  }),

  // 이벤트 목록(간단 커서) - Admin BFF
  http.get('*/api/admin/users/:userId/events', ({ request }) => {
    const url = new URL(request.url);
    const path = url.pathname;
    const limit = Number(url.searchParams.get('limit') ?? '50');
    const now = new Date();
    const rows = Array.from({ length: limit }).map((_, i) => ({
      id: crypto.randomUUID(),
      provider: 'google',
      kind: 'login',
      detail: null as any,
      ip: '203.0.113.10',
      userAgent: 'Mozilla/5.0 (mock)',
      createdAt: new Date(now.getTime() - i * 60000).toISOString(),
    }));
    return HttpResponse.json({
      timestamp: now.toISOString(),
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공적으로 처리되었습니다',
      path,
      traceId: 'mock',
      data: rows,
    });
  }),

  // 프로필 업데이트 - BFF
  http.post('*/api/users/profile', async ({ request }) => {
    const body = (await request.json()) as {
      nickname?: string | null;
      metadata?: Record<string, unknown> | null;
    };
    const now = new Date().toISOString();
    mockUser = {
      ...mockUser,
      nickname: body.nickname ?? mockUser.nickname,
      metadata: body.metadata ?? mockUser.metadata,
      updatedAt: now,
    } as typeof mockUser;
    return HttpResponse.json({
      timestamp: now,
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공적으로 처리되었습니다',
      path: '/api/users/profile',
      traceId: 'mock',
      data: mockUser,
    });
  }),

  // 링크/언링크 엔드포인트는 사용하지 않음(startLink/oauth 플로우 사용)

  // 계정 삭제 (로컬 목) - BFF
  http.delete('*/api/users/me', () =>
    HttpResponse.json({ ok: true }, { status: 204 }),
  ),
];
