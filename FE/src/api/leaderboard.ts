import { api } from './instance';
import type {
  LeaderboardPeriodKey,
  LeaderboardSubject,
  LeaderboardView,
  LeaderboardViewResponse,
} from './types';

export type LeaderboardFetchParams = {
  subject: LeaderboardSubject;
  key: LeaderboardPeriodKey;
  size?: number;
};

export async function fetchLeaderboard(
  params: LeaderboardFetchParams,
): Promise<LeaderboardView> {
  const { subject, key, size = 50 } = params;
  const res = await api.get('/leaderboard', {
    params: {
      subject,
      key,
      size,
    },
  });
  const data = res.data as LeaderboardViewResponse;
  return data.data;
}

