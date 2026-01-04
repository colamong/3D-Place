import { http, HttpResponse } from 'msw';
import { PLAYER_ENTRIES, CLAN_ENTRIES } from '@/mocks/leaderboard.mock';
import { authHandlers } from './auth.mock';
import { userHandlers } from './user.mock';
import { mockOpsDashboard } from './admin.mock';
import { clanHandlers } from './clan.mock';
import type { ModuleId } from '@/types/admin';

const leaderboardEntries = {
  USER: PLAYER_ENTRIES,
  CLAN: CLAN_ENTRIES,
} as const;

function randomTraceId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).slice(2);
}

function commonResponse(path: string, data: unknown) {
  return {
    timestamp: new Date().toISOString(),
    status: 200,
    code: 'SUCCESS',
    message: '요청이 성공적으로 처리되었습니다',
    path,
    traceId: randomTraceId(),
    data,
  };
}

function leaderboardHandler() {
  return http.get('/api/leaderboard', ({ request }) => {
    const url = new URL(request.url);
    const subjectParam = (url.searchParams.get('subject') ?? 'USER').toUpperCase();
    const keyParam = (url.searchParams.get('key') ?? 'DAY').toUpperCase();
    const sizeParsed = Number(url.searchParams.get('size') ?? '50');
    const size = Number.isNaN(sizeParsed) ? 50 : Math.max(1, Math.min(sizeParsed, 100));

    const subject = subjectParam === 'CLAN' ? 'CLAN' : 'USER';
    const key = keyParam === 'WEEK' ? 'WEEK' : keyParam === 'MONTH' ? 'MONTH' : 'DAY';
    const entries = leaderboardEntries[subject].slice(0, size);

    return HttpResponse.json(
      commonResponse('/api/leaderboard', {
        subject,
        key,
        entries,
      }),
    );
  });
}

// 코어(항상 등록) 핸들러들
export const coreHandlers = [
  // 리더보드
  leaderboardHandler(),

  // 미디어 업로드: presign
  http.post('*/api/uploads/presign', async ({ request }) => {
    const now = new Date().toISOString();
    const body = (await request.json()) as any;
    const assetId = crypto.randomUUID();
    const key = `assets/${(body?.subjectId ?? 'user').slice(0, 8)}/${assetId}`;
    const url = `https://mock-storage.local/${key}?signature=${crypto.randomUUID()}`;
    const headers: Record<string, string[]> = {
      'Content-Type': [body?.contentType ?? 'image/jpeg'],
    };
    if (body?.checksumSHA256Base64) headers['x-amz-content-sha256'] = [
      body.checksumSHA256Base64,
    ];
    return HttpResponse.json({
      timestamp: now,
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공적으로 처리되었습니다',
      path: '/api/uploads/presign',
      traceId: 'mock',
      data: {
        assetId,
        method: 'PUT',
        url,
        headers,
        key,
        expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
      },
    });
  }),

  // 미디어 업로드: commit
  http.post('*/api/uploads/commit', async ({ request }) => {
    const now = new Date().toISOString();
    const body = (await request.json()) as any;
    return HttpResponse.json({
      timestamp: now,
      status: 200,
      code: 'SUCCESS',
      message: '요청이 성공적으로 처리되었습니다',
      path: '/api/uploads/commit',
      traceId: 'mock',
      data: {
        assetId: body?.assetId ?? crypto.randomUUID(),
        assetKey: body?.objectKey ?? 'assets/mock/key',
        size: 0,
        width: 0,
        height: 0,
        mime: 'image/jpeg',
        sha256: '',
        pixelSha256: '',
        cdnUrl: `https://cdn.mock.local/${body?.objectKey ?? 'assets/mock/key'}`,
      },
    });
  }),

  // 모의 스토리지 PUT (CORS 포함)
  http.options('https://mock-storage.local/:rest*', () => {
    return new HttpResponse(null, {
      status: 204,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'PUT,OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type,x-amz-content-sha256',
      },
    });
  }),
  http.put('https://mock-storage.local/:rest*', async ({ request }) => {
    // 단순히 업로드 수신 OK. ETag를 돌려준다.
    const etag = '"mock-etag-12345"';
    return new HttpResponse(null, {
      status: 200,
      headers: {
        ETag: etag,
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Expose-Headers': 'ETag',
      },
    });
  }),

  // 모의 CDN 리소스 응답 (업로드 후 표시용)
  http.get('https://cdn.mock.local/:rest*', () => {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
      <defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop offset="0%" stop-color="#e0e7ff"/><stop offset="100%" stop-color="#93c5fd"/></linearGradient></defs>
      <rect width="128" height="128" fill="url(#g)"/>
      <circle cx="64" cy="64" r="36" fill="#fff" opacity="0.6"/>
    </svg>`;
    return new HttpResponse(svg, {
      status: 200,
      headers: {
        'Content-Type': 'image/svg+xml',
        'Access-Control-Allow-Origin': '*',
      },
    });
  }),

  // 관리자 페이지 관련 api 목 핸들러
  http.get('/api/admin/dashboard', ({ request }) => {
    const url = new URL(request.url);
    const scope = (url.searchParams.get('scope') ?? 'all') as ModuleId;
    const payload = mockOpsDashboard(); // 또는 mockOpsDashboardByScope(scope)
    return HttpResponse.json(payload);
  }),

  http.post('/api/admin/dlq/reprocess', async ({ request }) => {
    const body = await request.json();
    const { topic, dryRun } = body as { topic: string; dryRun?: boolean };
    return HttpResponse.json(
      { ok: true, processed: dryRun ? 0 : 123 },
      { status: 200 },
    );
  }),
];

// 플래그에 따라 도메인별 핸들러를 선택적으로 등록
export function buildHandlers() {
  const useAuth = (import.meta.env.VITE_MSW_AUTH ?? 'false') === 'true';
  const useUser = (import.meta.env.VITE_MSW_USER ?? 'true') === 'true';
  const useClan = (import.meta.env.VITE_MSW_CLAN ?? 'true') === 'true';

  const list: any[] = [...coreHandlers];
  if (useUser) list.push(...userHandlers);
  if (useAuth) list.push(...authHandlers);
  if (useClan) list.push(...clanHandlers);
  return list;
}

// 기본 export는 빌드 타임 플래그를 반영한 핸들러 목록
export const handlers = buildHandlers();
