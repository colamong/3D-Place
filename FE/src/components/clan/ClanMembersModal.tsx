import type React from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronLeft, UsersRound } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { clanKeys } from '@/features/clan/queries/key';
import { AxiosError } from 'axios';

type MinimalClan = { id: string };
import {
  useClanDetailViewQuery,
  useChangeMemberStatusMutation,
  useChangeMemberRoleMutation,
  useChangeOwnerMutation,
} from '@/features/clan/queries/query';
import {
  useJoinRequestsQuery,
  useApproveJoinRequestMutation,
  useRejectJoinRequestMutation,
} from '@/features/clan/queries/query';
import { cn } from '@/lib/cn';
import {
  MemberRow,
  BannedRow,
  JoinRequestRow,
  ConfirmDialog,
} from './MemberRows';
import { useMeQuery } from '@/features/user/queries/query';
import type { CommonResponse, ClanDetailView } from '@/api/types';

type MemberRole = 'MASTER' | 'OFFICER' | 'MEMBER';

type Member = {
  id: string;
  nickname: string;
  nicknameHandle?: string;
  role: MemberRole;
  isOwner?: boolean;
  avatarColor?: string;
};

type ClanMembersModalProps = {
  clan: MinimalClan | null;
  clanName: string;
  onBack: () => void;
  onRosterChange?: (memberCount: number, bannedCount: number) => void;
};

const DEFAULT_COLORS = ['#f97316', '#7c3aed', '#22c55e', '#0ea5e9', '#facc15'];

function mapRoleToUi(role: string): MemberRole {
  return ((): MemberRole => {
    const up = (role || '').toUpperCase();
    if (up === 'MASTER') return 'MASTER';
    if (up === 'OFFICER') return 'OFFICER';
    return 'MEMBER';
  })();
}

export default function ClanMembersModal({
  clan,
  clanName,
  onBack,
  onRosterChange,
}: ClanMembersModalProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const qc = useQueryClient();
  const [activeTab, setActiveTab] = useState<
    'users' | 'banned' | 'join-request'
  >('users');
  const { data, isLoading } = useClanDetailViewQuery(clan?.id ?? '', {
    enabled: Boolean(clan?.id),
  } as any);
  const { mutateAsync: setStatusAsync } = useChangeMemberStatusMutation(
    clan?.id ?? '',
  );
  const { mutateAsync: setRoleAsync } = useChangeMemberRoleMutation(
    clan?.id ?? '',
  );
  const { mutateAsync: makeOwnerAsync } = useChangeOwnerMutation(
    clan?.id ?? '',
  );
  const { data: reqRes } = useJoinRequestsQuery(clan?.id ?? '', {
    enabled: Boolean(clan?.id),
  } as any);
  const { mutateAsync: approveReqAsync } = useApproveJoinRequestMutation(
    clan?.id ?? '',
  );
  const { mutateAsync: rejectReqAsync } = useRejectJoinRequestMutation(
    clan?.id ?? '',
  );
  const { data: me } = useMeQuery();
  const [members, setMembers] = useState<Member[]>([]);
  const [banned, setBanned] = useState<Member[]>([]);
  const [portalTarget, setPortalTarget] = useState<HTMLElement | undefined>(
    undefined,
  );
  const [requests, setRequests] = useState<string[]>([]);
  const [confirmReq, setConfirmReq] = useState<null | {
    type: 'approve' | 'reject';
    userId: string;
  }>(null);
  const lastCountsRef = useRef<{ members: number; banned: number }>({
    members: 0,
    banned: 0,
  });
  const rosterKey = clan?.id ?? 'default';
  const refreshJoinRequests = () => {
    if (!clan?.id) return;
    qc.invalidateQueries({ queryKey: clanKeys.joinRequests(clan.id) });
  };

  useEffect(() => {
    if (!portalTarget && containerRef.current) {
      setPortalTarget(containerRef.current);
    }
  }, [portalTarget]);

  useEffect(() => {
    const detailData = (data as CommonResponse<ClanDetailView> | undefined)
      ?.data;
    const rows = (detailData?.members ?? []) as Array<{
      userId: string;
      role: string;
      status: string;
      paintCountTotal: number;
      joinedAt: string;
      leftAt?: string | null;
      nickname?: string | null;
      nicknameHandle?: string | null;
    }>;
    const active = rows.filter(
      (r) => r.status === 'ACTIVE' || r.status === 'INVITED',
    );
    const bannedRows = rows.filter((r) => r.status === 'BANNED');
    const toMember = (r: any, idx: number): Member => ({
      id: r.userId,
      nickname: r.nickname || r.userId?.slice(0, 8) || `user-${idx + 1}`,
      nicknameHandle:
        r.nicknameHandle ||
        r.nickname ||
        r.userId?.slice(0, 8) ||
        `user-${idx + 1}`,
      role: mapRoleToUi(r.role),
      isOwner: (r.role || '').toUpperCase() === 'MASTER',
      avatarColor: DEFAULT_COLORS[idx % DEFAULT_COLORS.length],
    });
    setMembers(active.map(toMember));
    setBanned(bannedRows.map(toMember));
    setActiveTab('users');
  }, [rosterKey, data]);

  useEffect(() => {
    const rows: string[] = (reqRes as any)?.data ?? [];
    setRequests(rows);
  }, [reqRes]);

  const myId = me?.data?.userId;
  const myRole = useMemo(() => {
    const rows: Array<{ userId: string; role?: string }> = ((data as any)?.data
      ?.members ?? []) as any[];
    const mine = myId ? rows.find((r) => r.userId === myId) : undefined;
    return (mine?.role || '').toUpperCase();
  }, [data, myId]);
  const isMaster = myRole === 'MASTER';
  const isOfficer = myRole === 'OFFICER';
  const canManageMembers = isMaster;
  const canReviewRequests = isMaster || isOfficer;

  useEffect(() => {
    if (!onRosterChange) return;
    const prev = lastCountsRef.current;
    if (
      prev &&
      prev.members === members.length &&
      prev.banned === banned.length
    ) {
      return;
    }
    lastCountsRef.current = { members: members.length, banned: banned.length };
    onRosterChange(members.length, banned.length);
  }, [members.length, banned.length, onRosterChange]);

  const banMember = async (memberId: string) => {
    if (!clan?.id || !canManageMembers) return;
    await setStatusAsync({ targetUserId: memberId, newStatus: 'BANNED' });
    setMembers((prev) => prev.filter((m) => m.id !== memberId));
    const target = members.find((m) => m.id === memberId);
    if (target) setBanned((prev) => [...prev, target]);
  };

  const unbanMember = async (memberId: string) => {
    if (!clan?.id || !canManageMembers) return;
    await setStatusAsync({ targetUserId: memberId, newStatus: 'ACTIVE' });
    setBanned((prev) => prev.filter((m) => m.id !== memberId));
    const target = banned.find((m) => m.id === memberId);
    if (target) setMembers((prev) => [...prev, { ...target, role: 'MEMBER' }]);
  };

  const promoteToOfficer = async (memberId: string) => {
    if (!clan?.id || !canManageMembers) return;
    await setRoleAsync({ targetUserId: memberId, newRole: 'OFFICER' });
    setMembers((prev) =>
      prev.map((m) => (m.id === memberId ? { ...m, role: 'OFFICER' } : m)),
    );
  };

  const demoteToMember = async (memberId: string) => {
    if (!clan?.id || !canManageMembers) return;
    await setRoleAsync({ targetUserId: memberId, newRole: 'MEMBER' });
    setMembers((prev) =>
      prev.map((m) => (m.id === memberId ? { ...m, role: 'MEMBER' } : m)),
    );
  };

  const makeOwner = async (memberId: string) => {
    if (!clan?.id || !canManageMembers) return;
    await makeOwnerAsync(memberId);
    setMembers((prev) =>
      prev.map((m) => ({ ...m, isOwner: m.id === memberId })),
    );
  };

  const kickMember = async (memberId: string) => {
    if (!clan?.id || !canManageMembers) return;
    await setStatusAsync({ targetUserId: memberId, newStatus: 'KICKED' });
    setMembers((prev) => prev.filter((m) => m.id !== memberId));
  };

  const sortedMembers = useMemo(() => {
    const rank: Record<MemberRole, number> = {
      MASTER: 0,
      OFFICER: 1,
      MEMBER: 2,
    };
    return [...members].sort((a, b) => {
      const ra = rank[a.role];
      const rb = rank[b.role];
      if (ra !== rb) return ra - rb;
      return a.nickname.localeCompare(b.nickname);
    });
  }, [members]);

  const sortedBanned = useMemo(
    () => [...banned].sort((a, b) => a.nickname.localeCompare(b.nickname)),
    [banned],
  );

  const Tab = ({
    id,
    label,
    count,
  }: {
    id: 'users' | 'banned' | 'join-request';
    label: string;
    count?: number;
  }) => (
    <button
      type="button"
      onClick={() => setActiveTab(id)}
      className={cn(
        'relative pb-2 text-base font-medium text-gray-500 cursor-pointer',
        activeTab === id && 'text-black',
      )}
    >
      <span className="inline-flex items-center gap-1">
        {label}
        {!!count && count > 0 && (
          <span className="ml-1 inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-gray-200 text-gray-700 text-xs font-semibold">
            {count}
          </span>
        )}
      </span>
      {activeTab === id && (
        <span className="absolute left-0 right-0 -bottom-0.5 h-[2px] rounded-full bg-black" />
      )}
    </button>
  );

  return (
    <div ref={containerRef} className="flex-1 flex flex-col">
      <header className="flex items-center gap-3 mb-4">
        <button
          type="button"
          onClick={onBack}
          className="rounded-full hover:bg-gray-100 cursor-pointer"
          aria-label="Back"
        >
          <ChevronLeft />
        </button>
        <div className="flex flex-col">
          <span className="flex items-center gap-2 text-sm text-gray-500">
            <UsersRound size={16} />
            {clanName}
          </span>
          <span className="text-2xl font-semibold">Members</span>
        </div>
      </header>

      <div className="flex gap-6 border-b border-gray-200 mb-4">
        <Tab id="users" label="Users" />
        {/* <Tab id="banned" label="Banned" /> */}
        <Tab id="join-request" label="Join requests" count={requests.length} />
      </div>

      <section className="flex-1 overflow-y-auto pr-1">
        {activeTab === 'users' &&
          (sortedMembers.length > 0 ? (
            <ul className="space-y-2">
              {sortedMembers.map((member) => (
                <MemberRow
                  key={member.id}
                  member={member}
                  canManage={canManageMembers}
                  onBan={banMember}
                  onKick={kickMember}
                  onPromote={promoteToOfficer}
                  onDemote={demoteToMember}
                  onMakeOwner={makeOwner}
                  portalTarget={
                    portalTarget ?? containerRef.current ?? undefined
                  }
                />
              ))}
            </ul>
          ) : (
            <EmptyState message="No members" />
          ))}

        {/* {activeTab === 'banned' &&
          (sortedBanned.length > 0 ? (
            <ul className="space-y-2">
              {sortedBanned.map((member) => (
                <BannedRow
                  key={member.id}
                  member={member}
                  canManage={canManageMembers}
                  onUnban={unbanMember}
                />
              ))}
            </ul>
          ) : (
            <EmptyState message="No banned users" />
          ))} */}

        {activeTab === 'join-request' &&
          (requests.length > 0 ? (
            <ul className="space-y-2">
              {requests.map((userId) => (
                <JoinRequestRow
                  key={userId}
                  userId={userId}
                  canManage={canReviewRequests}
                  onApprove={(id) =>
                    setConfirmReq({ type: 'approve', userId: id })
                  }
                  onReject={(id) =>
                    setConfirmReq({ type: 'reject', userId: id })
                  }
                />
              ))}
            </ul>
          ) : (
            <EmptyState message="No join requests" />
          ))}
      </section>

      {confirmReq && (
        <ConfirmDialog
          title={
            confirmReq.type === 'approve'
              ? 'Approve join request?'
              : 'Reject join request?'
          }
          description={
            confirmReq.type === 'approve'
              ? 'The user will become a member of this clan.'
              : 'The user’s request will be declined.'
          }
          confirmText={confirmReq.type === 'approve' ? 'Approve' : 'Reject'}
          cancelText="Cancel"
          onCancel={() => setConfirmReq(null)}
          onConfirm={async () => {
            const id = confirmReq.userId;
            const action = confirmReq.type;
            setConfirmReq(null);
            if (!canReviewRequests) return;
            try {
              if (action === 'approve') await approveReqAsync(id);
              else await rejectReqAsync(id);
            } catch (err) {
              if (
                action === 'approve' &&
                err instanceof AxiosError &&
                err.response?.status === 409
              ) {
                window.alert('이미 클랜에 가입된 사용자입니다.');
                try {
                  await rejectReqAsync(id);
                } catch (rejectErr) {
                  console.warn('Auto-reject failed', rejectErr);
                }
              } else {
                window.alert('요청 처리 중 오류가 발생했습니다.');
              }
            } finally {
              refreshJoinRequests();
            }
          }}
        />
      )}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="h-full flex items-center justify-center text-gray-400">
      {message}
    </div>
  );
}
