import type { LeaderboardPeriodKey, LeaderboardSubject } from '@/api/types';

export const leaderboardKeys = {
  all: ['leaderboard'] as const,
  entries: (subject: LeaderboardSubject, key: LeaderboardPeriodKey, size: number) =>
    ['leaderboard', subject, key, size] as const,
};
