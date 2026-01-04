import { useQuery } from '@tanstack/react-query';

import { fetchLeaderboard } from '@/api/leaderboard';
import type { LeaderboardPeriodKey, LeaderboardSubject } from '@/api/types';
import { leaderboardKeys } from './key';

export type LeaderboardQueryOptions = {
  key?: LeaderboardPeriodKey;
  size?: number;
};

const DEFAULT_KEY: LeaderboardPeriodKey = 'DAY';
const DEFAULT_SIZE = 50;

function normalizeOptions(options?: LeaderboardQueryOptions) {
  const size = Math.max(1, Math.min(options?.size ?? DEFAULT_SIZE, 100));
  return {
    key: options?.key ?? DEFAULT_KEY,
    size,
  };
}

function useLeaderboard(subject: LeaderboardSubject, options?: LeaderboardQueryOptions) {
  const normalized = normalizeOptions(options);
  return useQuery({
    queryKey: leaderboardKeys.entries(subject, normalized.key, normalized.size),
    queryFn: () =>
      fetchLeaderboard({
        subject,
        key: normalized.key,
        size: normalized.size,
      }),
    staleTime: 5_000,
  });
}

export function useUserLeaderboard(options?: LeaderboardQueryOptions) {
  return useLeaderboard('USER', options);
}

export function useClanLeaderboard(options?: LeaderboardQueryOptions) {
  return useLeaderboard('CLAN', options);
}
