// src/mocks/auth.mock.ts
import { http, HttpResponse } from 'msw';

// 서버 흉내용: 메모리 세션 테이블
const sessions = new Map<string, { sub: string; email: string }>();

function cookie(name: string, val: string, maxAgeSec: number) {
  return `${name}=${val}; Path=/; Max-Age=${maxAgeSec}; SameSite=Lax`;
}

export const authHandlers = [
  // 1) 세션 조회
  http.get('/api/auth/state', ({ request, cookies }) => {
    const sid =
      cookies?.AUTHSESSION ||
      /AUTHSESSION=([^;]+)/.exec(request.headers.get('cookie') || '')?.[1];

    if (!sid) {
      return HttpResponse.json(
        { authenticated: false, next_action: 'LOGIN' },
        { status: 200 },
      );
    }

    const user = sessions.get(sid) ?? {
      sub: 'mock|12345',
      email: 'user@example.com',
    };

    return HttpResponse.json(
      {
        authenticated: true,
        sub: user.sub,
        email: user.email,
        email_verified: true,
        next_action: 'NONE',
      },
      { status: 200 },
    );
  }),

  // 2) 로그인 트리거: 302로 Mock SSO로 이동
  http.get('/api/login', ({ request }) => {
    const url = new URL(request.url);
    const next = url.searchParams.get('next') || '/';
    const state = crypto.randomUUID();

    const ssoUrl = new URL('/mock-sso', window.location.origin);
    ssoUrl.searchParams.set('state', state);
    // React 라우트 컴포넌트(MockSso.tsx)가 사용하는 파라미터명과 맞춤
    ssoUrl.searchParams.set('redirect', next);

    return new HttpResponse(null, {
      status: 302,
      headers: { Location: ssoUrl.toString(), 'Cache-Control': 'no-store' },
    });
  }),

  // 3) Mock SSO 페이지는 React Router가 렌더링하도록 별도 핸들러 없음

  // 4) 콜백: 세션 생성 후 JSON 반환(React 컴포넌트에서 제어)
  http.get('/api/login/oauth2/code/auth0', ({ request }) => {
    const url = new URL(request.url);
    const next = url.searchParams.get('redirect') || '/';

    const sid = crypto.randomUUID();
    sessions.set(sid, { sub: 'mock|12345', email: 'user@example.com' });

      const headers = new Headers();
      [
        cookie('AUTHSESSION', sid, 3600),
        cookie('XSRF-TOKEN', crypto.randomUUID(), 3600),
      ].forEach((c) => headers.append('Set-Cookie', c));
      headers.append('Cache-Control', 'no-store');

    return HttpResponse.json(
      { sid, redirect: next },
      {
        status: 200,
        headers
      },
    );
  }),

  // 5) 로그아웃: 세션 제거 후 302
  http.post('/api/logout', ({ request, cookies }) => {
    const sid =
      cookies?.AUTHSESSION ||
      /AUTHSESSION=([^;]+)/.exec(request.headers.get('cookie') || '')?.[1];

    if (sid) sessions.delete(sid);

    const headers = new Headers();
    [cookie('AUTHSESSION', '', 0), cookie('XSRF-TOKEN', '', 0)].forEach((c) =>
      headers.append('Set-Cookie', c),
    );
    headers.append('Location', '/');
    headers.append('Cache-Control', 'no-store');

    return new HttpResponse(null, {
      status: 302,
      headers
    });
  }),
  http.get('/api/logout', ({ request, cookies }) => {
    const sid =
      cookies?.AUTHSESSION ||
      /AUTHSESSION=([^;]+)/.exec(request.headers.get('cookie') || '')?.[1];

    if (sid) sessions.delete(sid);

    const headers = new Headers();
    [cookie('AUTHSESSION', '', 0), cookie('XSRF-TOKEN', '', 0)].forEach((c) =>
      headers.append('Set-Cookie', c),
    );
    headers.append('Location', '/');
    headers.append('Cache-Control', 'no-store');

    return new HttpResponse(null, {
      status: 302,
      headers
    });
  }),

  // 6) 토큰/세션 연장(샘플)
  http.post('/auth/refresh', () => {
    return HttpResponse.json({ ok: true }, { status: 200 });
  }),

  // 7) (옵션) frontChannelLogout 도착지
  http.get(
    '/mock-logged-out',
    () =>
      new HttpResponse(
        '<html><body>Logged out<script>location="/";</script></body></html>',
        {
          headers: { 'Content-Type': 'text/html' },
        },
      ),
  ),
];
