import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronLeft, Search } from 'lucide-react';
import { cn } from '@/lib/cn';
import { usePublicClansQuery } from '@/features/clan/queries/query';
import { useQueryClient } from '@tanstack/react-query';
import { clanKeys } from '@/features/clan/queries/key';
import { cancelJoinRequest, joinClan } from '@/api/clan';
import { AxiosError } from 'axios';
import type {
  CommonResponse,
  ClanSummaryResponse,
  PublicClanSummaryResponse,
} from '@/api/types';

type PublicClan = PublicClanSummaryResponse | ClanSummaryResponse;

function toSummary(item: PublicClan): ClanSummaryResponse {
  if ((item as PublicClanSummaryResponse).clan) {
    return (item as PublicClanSummaryResponse).clan;
  }
  return item as ClanSummaryResponse;
}

export default function ClanSearch({
  onBack,
  onJoined,
}: {
  onBack: () => void;
  onJoined?: (c: {
    id: string;
    name: string;
    members: number;
    pixelsPainted: number;
  }) => void;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [policies, setPolicies] = useState<
    Array<'OPEN' | 'APPROVAL'>
  >(['OPEN', 'APPROVAL']);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  const { data, isLoading, isError } = usePublicClansQuery({
    q: debouncedQ || undefined,
    policies,
    limit: 20,
  });

  const rows: PublicClan[] = useMemo(() => {
    const response = data as CommonResponse<PublicClan[]> | undefined;
    return response?.data ?? [];
  }, [data]);

  const [localPendingIds, setLocalPendingIds] = useState<Set<string>>(
    () => new Set(),
  );
  const [joiningId, setJoiningId] = useState<string | null>(null);
  const [requestingId, setRequestingId] = useState<string | null>(null);
  const [cancelingId, setCancelingId] = useState<string | null>(null);
  const qc = useQueryClient();
  const invalidatePublicList = () =>
    qc.invalidateQueries({ queryKey: ['clan', 'public'] as const });

  useEffect(() => {
    const serverPendingIds = new Set<string>();
    rows.forEach((item) => {
      const status = (item as PublicClanSummaryResponse).myJoinRequestStatus;
      if (status === 'PENDING') {
        serverPendingIds.add(toSummary(item).id);
      }
    });
    setLocalPendingIds((prev) => {
      if (prev.size === 0) return prev;
      let changed = false;
      const next = new Set(prev);
      next.forEach((id) => {
        if (!serverPendingIds.has(id)) {
          next.delete(id);
          changed = true;
        }
      });
      return changed ? next : prev;
    });
  }, [rows]);

  const markPending = (id?: string) => {
    if (!id) return;
    setLocalPendingIds((prev) => {
      if (prev.has(id)) return prev;
      const next = new Set(prev);
      next.add(id);
      return next;
    });
  };

  const handleJoinError = (err: unknown, defaultMessage: string) => {
    if (err instanceof AxiosError && err.response?.status === 409) {
      window.alert('이미 가입 신청을 한 상태입니다.');
      return;
    }
    window.alert(defaultMessage);
  };

  const togglePolicy = (p: 'OPEN' | 'APPROVAL') => {
    setPolicies((prev) =>
      prev.includes(p) ? prev.filter((x) => x !== p) : [...prev, p],
    );
  };

  const policyBadge = (policy: 'OPEN' | 'APPROVAL' | 'INVITE_ONLY') => {
    const badge =
      policy === 'OPEN'
        ? 'bg-green-100 text-green-700 border-green-200'
        : policy === 'APPROVAL'
        ? 'bg-amber-100 text-amber-700 border-amber-200'
        : 'bg-slate-100 text-slate-700 border-slate-200';
    const label =
      policy === 'OPEN' ? 'Open' : policy === 'APPROVAL' ? 'Approval' : 'Invite only';
    return (
      <span
        className={cn(
          'inline-block rounded-full border px-2 py-0.5 text-xs font-semibold',
          badge,
        )}
        title={`Join policy: ${policy}`}
      >
        {label}
      </span>
    );
  };

  const handleJoin = async (summary: ClanSummaryResponse) => {
    if (!summary?.id) return;
    setJoiningId(summary.id);
    try {
      await joinClan(summary.id);
      // 관련 키 무효화
      qc.invalidateQueries({ queryKey: clanKeys.me() });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(summary.id) });
      // prefix 매칭으로 다양한 params 조합을 모두 무효화
      invalidatePublicList();
      onJoined?.({
        id: summary.id,
        name: summary.name,
        members: summary.memberCount,
        pixelsPainted: summary.paintCountTotal,
      });
    } catch (err) {
      handleJoinError(err, '클랜 가입 중 오류가 발생했습니다.');
    }
    setJoiningId(null);
  };

  const handleRequest = async (summary: ClanSummaryResponse) => {
    if (!summary?.id) return;
    setRequestingId(summary.id);
    try {
      await joinClan(summary.id);
      markPending(summary.id);
      qc.invalidateQueries({ queryKey: clanKeys.detailView(summary.id) });
      invalidatePublicList();
    } catch (err) {
      if (err instanceof AxiosError && err.response?.status === 409) {
        markPending(summary.id);
        window.alert('이미 해당 클랜에 가입 신청을 보내두었습니다.');
      } else {
        window.alert('가입 신청 중 오류가 발생했습니다.');
      }
    }
    setRequestingId(null);
  };

  const handleCancelRequest = async (summary: ClanSummaryResponse) => {
    if (!summary?.id) return;
    setCancelingId(summary.id);
    try {
      await cancelJoinRequest(summary.id);
      setLocalPendingIds((prev) => {
        if (!prev.has(summary.id)) return prev;
        const next = new Set(prev);
        next.delete(summary.id);
        return next;
      });
      qc.invalidateQueries({ queryKey: clanKeys.detailView(summary.id) });
      invalidatePublicList();
    } catch (err) {
      window.alert("가입 신청을 취소하는 중 오류가 발생했습니다.");
    }
    setCancelingId(null);
  };

  return (
    <div ref={containerRef} className="h-full flex flex-col overflow-hidden">
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
          <span className="text-2xl font-semibold">Search Clans</span>
        </div>
      </header>

      <section className="mb-3">
        <div className="flex items-center gap-2 border border-gray-300 rounded-full px-3 py-2">
          <Search size={18} className="text-gray-500" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search by name"
            className="flex-1 outline-none text-sm"
          />
        </div>
        <div className="mt-3 flex gap-2">
          {(['OPEN', 'APPROVAL'] as const).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => togglePolicy(p)}
              className={cn(
                'px-3 py-1 rounded-full border text-sm cursor-pointer',
                policies.includes(p)
                  ? 'bg-black text-white border-black'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50',
              )}
            >
              {p === 'OPEN' ? 'Open' : 'Approval'}
            </button>
          ))}
        </div>
      </section>

      <section className="flex-1 min-h-0 overflow-y-auto pr-1">
        {isLoading ? (
          <div className="py-24 text-center text-gray-400">Loading</div>
        ) : isError ? (
          <div className="py-24 text-center text-red-500">Failed to load</div>
        ) : rows.length === 0 ? (
          <div className="py-24 text-center text-gray-400">No clans found</div>
        ) : (
          <ul className="space-y-2">
            {rows.map((item, idx) => {
              const summary = toSummary(item);
              const policy = summary.joinPolicy;
              const status =
                (item as PublicClanSummaryResponse).myJoinRequestStatus ?? null;
              const isPending =
                status === 'PENDING' || localPendingIds.has(summary.id);
              const isRequesting = requestingId === summary.id;
              const isCanceling = cancelingId === summary.id;
              const waiting = isRequesting || isCanceling;

              return (
                <li
                  key={summary.id ?? `${summary.name}-${idx}`}
                  className="flex items-center justify-between rounded-2xl px-3 py-3 hover:bg-gray-50"
                >
                  <div className="flex flex-col">
                    <div className="flex items-center gap-2">
                      <span className="text-blue-600 font-semibold">{summary.name}</span>
                      {policyBadge(policy)}
                    </div>
                    <div className="text-gray-600 text-sm">
                      <span className="mr-3">Members: {summary.memberCount}</span>
                      <span>
                        Pixels painted: {(summary.paintCountTotal ?? 0).toLocaleString()}
                      </span>
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    {policy === 'OPEN' ? (
                      <button
                        type="button"
                        disabled={joiningId === summary.id}
                        onClick={() => handleJoin(summary)}
                        className={cn(
                          'rounded-full px-4 py-1 text-sm font-semibold border cursor-pointer',
                          joiningId === summary.id
                            ? 'bg-gray-200 text-gray-500 border-gray-200'
                            : 'border-gray-300 hover:bg-gray-100',
                        )}
                      >
                        {joiningId === summary.id ? 'Joining...' : 'Join'}
                      </button>
                    ) : policy === 'APPROVAL' ? (
                      <button
                        type="button"
                        disabled={waiting}
                        onClick={() => {
                          if (isPending) {
                            const confirmed = window.confirm(
                              '가입을 대기중입니다. 신청을 철회하시겠습니까?',
                            );
                            if (confirmed) {
                              void handleCancelRequest(summary);
                            }
                            return;
                          }
                          void handleRequest(summary);
                        }}
                        className={cn(
                          'rounded-full px-4 py-1 text-sm font-semibold border cursor-pointer',
                          waiting
                            ? 'bg-gray-200 text-gray-500 border-gray-200'
                            : 'border-gray-300 hover:bg-gray-100',
                        )}
                      >
                        {isPending
                          ? (isCanceling ? 'Cancelling...' : 'Waiting')
                          : isRequesting
                            ? 'Requesting...'
                            : 'Request to join'}
                      </button>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

