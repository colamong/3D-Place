type ClanEditMode = 'name' | 'description' | 'policy';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { Column } from '../common/LiteTable';
import LiteTable from '../common/LiteTable';
import { cn } from '@/lib/cn';
import { EllipsisVertical, UserRoundPlus, Pencil } from 'lucide-react';
import DockPopover from '../common/DockPopover';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  useClanDetailViewQuery,
  useLeaveClanMutation,
  useDeleteClanMutation,
} from '@/features/clan/queries/query';
import { useMeQuery } from '@/features/user/queries/query';
import { useUserLeaderboard } from '@/features/leaderboard/queries/query';
import type { CommonResponse, ClanDetailView } from '@/api/types';

export function ClanHome({
  clan,
}: {
  clan: { id: string; name: string; pixelsPainted: number; members: number };
}) {
  const leaderboardSize = 100;
  const { data: leaderboard } = useUserLeaderboard({ size: leaderboardSize });
  const leaderboardEntries = leaderboard?.entries ?? [];
  const leaderboardPaintsMap = useMemo(() => {
    const map = new Map<string, number>();
    leaderboardEntries.forEach((entry) => {
      if (entry?.id) {
        map.set(entry.id, entry.paints);
      }
    });
    return map;
  }, [leaderboardEntries]);

  const { mutateAsync: leave } = useLeaveClanMutation(clan.id);
  const { data: detailRes } = useClanDetailViewQuery(clan.id);
  const detailData = (detailRes as CommonResponse<ClanDetailView> | undefined)
    ?.data;
  const memberViews = detailData?.members ?? [];
  const serverDesc = detailData?.description ?? '';
  const [description, setDescription] = useState(serverDesc);
  const portalRef = useRef<HTMLDivElement | null>(null);
  const dotBtnRef = useRef<HTMLButtonElement | null>(null);
  const [leaveModalOpen, setLeaveModalOpen] = useState(false);

  const location = useLocation();
  const navigate = useNavigate();
  const bgLoc = (location.state as any)?.backgroundLocation ?? location;
  const bg = { backgroundLocation: bgLoc, desc: description };
  const joinPolicy = detailData?.joinPolicy as
    | 'OPEN'
    | 'APPROVAL'
    | 'INVITE_ONLY'
    | undefined;
  const openEditModal = (mode: ClanEditMode) => {
    navigate(`/clan/${clan.id}/editDesc`, {
      state: { ...bg, name: clan.name, policy: joinPolicy, mode },
    });
  };

  useEffect(() => {
    setDescription(serverDesc);
  }, [serverDesc]);

  const rows = useMemo(() => {
    const sortedMembers = [...memberViews].sort((a, b) => {
      const paintA =
        leaderboardPaintsMap.get(a.userId) ?? a.paintCountTotal ?? 0;
      const paintB =
        leaderboardPaintsMap.get(b.userId) ?? b.paintCountTotal ?? 0;
      return paintB - paintA;
    });

    return sortedMembers.map((member, idx) => ({
      id: member.id,
      rank: idx + 1,
      name: member.nickname,
      pixels:
        leaderboardPaintsMap.get(member.userId) ?? member.paintCountTotal ?? 0,
    }));
  }, [memberViews, leaderboardPaintsMap]);


  const columns: Column<(typeof rows)[number]>[] = [
    { key: 'rank', header: '#', width: 'w-10', render: (r) => r.rank },
    { key: 'name', header: 'Member', render: (r) => r.name },
    {
      key: 'pixels',
      header: 'Pixels painted',
      align: 'right',
      render: (r) => r.pixels.toLocaleString(),
    },
  ];

  const { data: me } = useMeQuery();
  const myId = me?.data?.userId;
  const myRole = useMemo(() => {
    const mine = myId
      ? memberViews.find((m) => m.userId === myId)
      : undefined;
    return (mine?.role || '').toUpperCase();
  }, [memberViews, myId]);
  const isMaster = myRole === 'MASTER';
  const isOfficer = myRole === 'OFFICER';
  const isMember = myRole === 'MEMBER';
  const isOwner = isMaster;
  const canEdit = isMaster;
  const canInvite = isMaster || isOfficer;
  const canViewRoster = isMaster || isOfficer || isMember;
  const { mutateAsync: del } = useDeleteClanMutation(clan.id);

  return (
    <div ref={portalRef} className="relative">
      <div className="flex flex-col mb-6 gap-2">
        <div className="flex justify-between">
          <header className="flex items-center text-center gap-2">
            <h2 className="text-3xl font-extrabold">
              {canEdit ? (
                <button
                  type="button"
                  onClick={() => openEditModal('name')}
                  title="Edit clan name"
                  className="text-left text-inherit bg-transparent border-none p-0 cursor-pointer"
                >
                  {clan.name}
                </button>
              ) : (
                clan.name
              )}
            </h2>
            {(() => {
              if (!joinPolicy) return null;
              const badge =
                joinPolicy === 'OPEN'
                  ? 'bg-green-100 text-green-700 border-green-200'
                  : joinPolicy === 'APPROVAL'
                    ? 'bg-amber-100 text-amber-700 border-amber-200'
                    : 'bg-slate-100 text-slate-700 border-slate-200';
              const label =
                joinPolicy === 'OPEN'
                  ? 'Open'
                  : joinPolicy === 'APPROVAL'
                    ? 'Approval'
                    : 'Invite only';
              const baseClasses = cn(
                'inline-block rounded-full border px-2 py-0.5 text-xs font-semibold',
                badge,
              );
              if (canEdit) {
                return (
                  <button
                    type="button"
                    className={cn(baseClasses, 'cursor-pointer')}
                    title={`Join policy: ${joinPolicy}`}
                    onClick={() => openEditModal('policy')}
                  >
                    {label}
                  </button>
                );
              }
              return (
                <span
                  className={baseClasses}
                  title={`Join policy: ${joinPolicy}`}
                >
                  {label}
                </span>
              );
            })()}
          </header>
          <div className="flex gap-1 items-center ">
            <div className="cursor-pointer px-4 py-1.5 hover:bg-gray-300 hover:rounded-3xl">
              <button
                ref={dotBtnRef}
                onClick={() => setLeaveModalOpen((v) => !v)}
                aria-haspopup="menu"
                aria-expanded={leaveModalOpen}
                className="flex justify-center items-center"
              >
                <EllipsisVertical className="cursor-pointer" />
              </button>
            </div>
            {canInvite && (
              <div className="border-2 border-gray-300 bg-gray-100 rounded-3xl px-5 py-2 cursor-pointer">
                <button
                  className="flex justify-center items-center"
                  type="button"
                  onClick={() =>
                    navigate(`/clan/${clan.id}/invite`, {
                      state: { backgroundLocation: bgLoc },
                    })
                  }
                >
                  <UserRoundPlus size={18} className="cursor-pointer" />
                </button>
              </div>
            )}
          </div>
          {leaveModalOpen && (
            <DockPopover
              anchorRef={dotBtnRef as React.RefObject<HTMLElement>}
              onClose={() => setLeaveModalOpen(false)}
              placement="top"
              offset={14}
              portalTarget={portalRef.current ?? undefined}
              className="w-20 mt-8 -ml-16"
              withArrow={false}
            >
              <div className="flex flex-col">
                {isOwner ? (
                  <button
                    type="button"
                    className="w-full text-left px-3 py-2 rounded-lg text-red-600 hover:bg-red-50 cursor-pointer"
                    onClick={async () => {
                      setLeaveModalOpen(false);
                      if (
                        confirm(
                          'Delete this clan? This action cannot be undone.',
                        )
                      ) {
                        try {
                          await del();
                          navigate('/');
                        } catch {}
                      }
                    }}
                  >
                    Delete clan
                  </button>
                ) : (
                  <button
                    type="button"
                    className="w-full text-left px-3 py-2 rounded-lg text-red-600 hover:bg-red-50 cursor-pointer"
                    onClick={() => {
                      setLeaveModalOpen(false);
                      void leave();
                    }}
                  >
                    Leave clan
                  </button>
                )}
              </div>
            </DockPopover>
          )}
        </div>

        {/* description */}
        <div className="flex gap-2 text-gray-500">
          <span className="ml-1">
            {description == '' ? 'No description' : description}
          </span>
          {canEdit && (
            <button
              type="button"
              className="cursor-pointer"
              onClick={() => openEditModal('description')}
              title="Edit clan"
            >
              <Pencil size={16} />
            </button>
          )}
        </div>
        <div className="flex flex-col text-gray-700 gap-0.5">
          <span className="flex gap-1 mr-3">
            Pixels painted: {clan.pixelsPainted.toLocaleString()}
          </span>

          <span className="flex gap-1 mr-3 items-center">
            Members:
            {canViewRoster ? (
              <button
                className="underline text-blue-600 font-semibold cursor-pointer"
                onClick={() =>
                  navigate(`/clan/${clan.id}/members`, {
                    state: { backgroundLocation: bgLoc, clanName: clan.name },
                  })
                }
                title="View members"
              >
                {clan.members}
              </button>
            ) : (
              <span className="text-gray-500" title="Only officers can view">
                {clan.members}
              </span>
            )}
          </span>
        </div>
      </div>

      {/* 기간 ??*/}
      <span className="text-2xl font-semibold">Leaderboard</span>
      <div className="min-h-[320px]">
        {(() => {
          if (!detailData) {
            return (
              <div className="py-24 text-center text-gray-400">Loading...</div>
            )
          }
          if (rows.length === 0) {
            return (
              <div className="py-24 text-center text-gray-400">
                No members have painted yet.
              </div>
            )
          }
          return (
            <LiteTable rows={rows} columns={columns} rowKey={(r) => r.id} />
          )
        })()}
      </div>
    </div>
  );
}
