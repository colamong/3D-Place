import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';

import { joinClanByInvite } from '@/api/clan';
import { useAuthStateQuery } from '@/features/auth/queries/query';
import { useMyClanQuery } from '@/features/clan/queries/query';
import { useQueryClient } from '@tanstack/react-query';
import { clanKeys } from '@/features/clan/queries/key';
import type { CommonResponse, MyClanResponse } from '@/api/types';

type JoinState =
  | { kind: 'checking'; message: string }
  | { kind: 'joining'; message: string }
  | { kind: 'invalid' }
  | { kind: 'already' };

export default function JoinPage() {
  const qc = useQueryClient();
  const [sp] = useSearchParams();
  const urlToken = sp.get('token') || '';
  const [token, setToken] = useState<string>(urlToken);
  const navigate = useNavigate();
  const location = useLocation();
  const [state, setState] = useState<JoinState>({
    kind: 'checking',
    message: 'Checking invite link...',
  });
  const attemptedJoinRef = useRef<string | null>(null);

  const { data: auth, isLoading: authLoading } = useAuthStateQuery();
  const {
    data: myClanRes,
    isLoading: myClanLoading,
    isFetching: myClanFetching,
    } = useMyClanQuery({
    enabled: !!auth?.authenticated,
  } as any);

  const myClanResponse = myClanRes as CommonResponse<MyClanResponse> | undefined;
  const myClanData = myClanResponse?.data?.clan;
  
  const isMyClanBusy =
    !!auth?.authenticated && (myClanLoading || myClanFetching);

  const nextUrl = useMemo(
    () => location.pathname + location.search,
    [location],
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      let t = token;
      if (!t) {
        const saved = sessionStorage.getItem('join_token');
        if (saved) {
          setToken(saved);
          t = saved;
        }
      }
      if (!t) {
        sessionStorage.removeItem('join_token');
        setState({ kind: 'invalid' });
        return;
      }
      if (authLoading || isMyClanBusy) return;
      if (!auth?.authenticated) {
        const gw = (import.meta.env.VITE_GATEWAY_URL ?? '').replace(/\/$/, '');
        const base = gw ? `${gw}/api/login` : '/api/login';
        sessionStorage.setItem('join_token', t);
        const url = `${base}?next=${encodeURIComponent(nextUrl)}`;
        window.location.replace(url);
        return;
      }

      if (myClanData?.id) {
        sessionStorage.removeItem('join_token');
        setState({ kind: 'already' });
        return;
      }

      if (attemptedJoinRef.current === t) {
        return;
      }
      attemptedJoinRef.current = t;
      setState({ kind: 'joining', message: 'Joining clan...' });

      try {
        await joinClanByInvite(t);
        if (cancelled) return;
        qc.invalidateQueries({ queryKey: clanKeys.me() });
        qc.invalidateQueries({ queryKey: clanKeys.public({}) });
        sessionStorage.removeItem('join_token');
        navigate('/clan', {
          replace: true,
          state: { backgroundLocation: { pathname: '/' } },
        });
      } catch (e) {
        if (cancelled) return;
        attemptedJoinRef.current = null;
        const status = (e as any)?.response?.status ?? 0;
        if (status === 409) {
          setState({ kind: 'already' });
        } else {
          sessionStorage.removeItem('join_token');
          setState({ kind: 'invalid' });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [
    token,
    auth?.authenticated,
    authLoading,
    isMyClanBusy,
    myClanData?.id,
    navigate,
    nextUrl,
  ]);

  if (state.kind === 'checking' || state.kind === 'joining') {
    return (
      <div className="min-h-screen grid place-items-center text-gray-600">
        {state.message}
      </div>
    );
  }

  if (state.kind === 'invalid') {
    return (
      <div className="min-h-screen grid place-items-center text-center text-gray-600">
        <div>
          <div className="mb-4 text-lg font-semibold">Invalid invite link</div>
          <p className="mb-6">The invite is expired or incorrect.</p>
          <button
            className="rounded-full bg-black text-white px-5 py-2 cursor-pointer"
            onClick={() => navigate('/', { replace: true })}
          >
            Go Home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen grid place-items-center">
      <div className="rounded-2xl border p-6 shadow-sm text-center">
        <div className="text-xl font-semibold mb-2">
          You already have a clan!
        </div>
        <p className="text-gray-600 mb-4">
          Leave your current clan before accepting new invites.
        </p>
        <button
          className="rounded-full bg-black text-white px-5 py-2 cursor-pointer"
          onClick={() => navigate('/', { replace: true })}
        >
          Go Home
        </button>
      </div>
    </div>
  );
}
