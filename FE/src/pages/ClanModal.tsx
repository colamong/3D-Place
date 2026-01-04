import React, { useEffect, useState } from 'react';
import {
  Routes,
  Route,
  useLocation,
  useNavigate,
  matchPath,
} from 'react-router-dom';
import ModalShell from '@/components/common/ModalShell';
import { UsersRound } from 'lucide-react';
import { useMyClanQuery } from '@/features/clan/queries/query';
import { useAuthStateQuery } from '@/features/auth/queries/query';

import { EmptyState } from '@/components/clan/EmptyState';
import { ClanHome } from '@/components/clan/ClanHome';
import { CreateDialog } from '@/components/clan/CreateDialog';
import ClanMembersModal from '@/components/clan/ClanMembersModal';
import EditClanInfoModal from '@/components/clan/EditClanInfoModal';
import InviteLinkModal from '@/components/clan/InviteLinkModal';
import { cn } from '@/lib/cn';
import ClanSearch from '@/components/clan/ClanSearch';
import type { CommonResponse, MyClanResponse } from '@/api/types';

type View = 'checking' | 'empty' | 'create' | 'search' | 'home';
export default function ClanModal() {
  const [view, setView] = useState<View>('checking');
  const [clan, setClan] = useState<{
    id: string;
    name: string;
    members: number;
    pixelsPainted: number;
  } | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const membersMatch = matchPath(
    { path: '/clan/:id/members', end: false },
    location.pathname,
  );
  const stateClanName = (location.state as { clanName?: string } | undefined)
    ?.clanName;
  const clanName = clan?.name ?? stateClanName ?? 'Clan';
  const isMembersView = !!membersMatch;

  const { data: auth, isLoading: authLoading } = useAuthStateQuery();
  const {
    data: myClanRes,
    isLoading: myClanLoading,
    isError: myClanError,
  } = useMyClanQuery();

  const membershipResponse = myClanRes as
    | CommonResponse<MyClanResponse>
    | undefined;
  const summary = membershipResponse?.data?.clan;

  useEffect(() => {
    // 인증 상태 판별 전이면 로딩 유지
    if (authLoading) {
      setView('checking');
      return;
    }
    // 비로그인: 로그인 페이지로 유도
    if (!auth?.authenticated) {
      setView('checking');
      const gw = (import.meta.env.VITE_GATEWAY_URL ?? '').replace(/\/$/, '');
      const base = gw ? `${gw}/api/login` : '/api/login';
      const nextUrl = location.pathname + location.search;
      window.location.replace(`${base}?next=${encodeURIComponent(nextUrl)}`);
      return;
    }

    if (myClanLoading) {
      setView('checking');
      return;
    }

    if (myClanError) {
      setErr('클랜 정보를 불러오지 못했습니다.');
      setView('empty');
      return;
    }

    if (summary && summary.id) {
      setClan({
        id: summary.id,
        name: summary.name,
        members: summary.memberCount ?? 0,
        pixelsPainted: summary.paintCountTotal ?? 0,
      });
      setView('home');
    } else {
      setClan(null);
      setView('empty');
    }
  }, [myClanLoading, myClanError, myClanRes, authLoading, auth?.authenticated]);

  return (
    <ModalShell contentId="clan-modal-content">
      <div className="h-full w-full flex flex-col overflow-hidden">
        {isMembersView ? (
          <ClanMembersModal
            clan={clan}
            clanName={clanName}
            onBack={() => navigate(-1)}
            onRosterChange={(memberCount, _bannedCount) =>
              setClan((prev) =>
                prev ? { ...prev, members: memberCount } : prev,
              )
            }
          />
        ) : (
          <>
            {/* 헤더 */}
            <header className="mb-4 flex items-center gap-2 text-xl md:text-3xl font-extrabold">
              <UsersRound className="w-8 h-8" />
              <span>Clan</span>
            </header>
            <main
              className={cn(
                'flex-1 overflow-hidden min-h-0 flex flex-col',
                (view === 'empty' || view === 'checking') &&
                  'grid place-items-center',
              )}
            >
              {view === 'checking' && (
                <div className="py-24 text-center text-gray-500">
                  불러오는 중…
                </div>
              )}

              {view === 'empty' && (
                <EmptyState
                  onCreate={() => setView('create')}
                  onSearch={() => setView('search')}
                />
              )}

              {view === 'create' && (
                <CreateDialog
                  onCancel={() => setView(clan ? 'home' : 'empty')}
                  onCreated={(c) => {
                    setClan(c);
                    setView('home');
                  }}
                />
              )}

              {view === 'search' && (
                <ClanSearch
                  onBack={() => setView(clan ? 'home' : 'empty')}
                  onJoined={(c) => {
                    setClan(c);
                    setView('home');
                  }}
                />
              )}

              {view === 'home' && clan && (
                <div className="flex-1 min-h-0 overflow-y-auto pr-1">
                  <ClanHome clan={clan} />
                </div>
              )}

              {err && (
                <p className="mt-4 text-sm text-red-600" role="alert">
                  {err}
                </p>
              )}
            </main>
          </>
        )}
      </div>
      <Routes>
        <Route path=":id/editDesc" element={<EditClanInfoModal />} />
        <Route path=":id/invite" element={<InviteLinkModal />} />
      </Routes>
    </ModalShell>
  );
}
