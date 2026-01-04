import LiteTable, { type Column } from '../common/LiteTable';
import type {
  LeaderboardEntryView,
  LeaderboardPeriodKey,
  LeaderboardSubject,
} from '@/api/types';
import {
  useClanLeaderboard,
  useUserLeaderboard,
} from '@/features/leaderboard/queries/query';

type LeaderboardViewProps = {
  period: LeaderboardPeriodKey;
  size?: number;
};

const DEFAULT_SIZE = 50;

function LoadingNotice({ isFetching }: { isFetching: boolean }) {
  if (!isFetching) return null;
  return (
    <div className="mb-2 text-xs text-gray-500">
      데이터를 불러오는 중입니다…
    </div>
  );
}

const emptyText = (isLoading: boolean) =>
  isLoading ? '데이터를 불러오는 중입니다…' : '기록된 데이터가 아직 없습니다.';

function LeaderboardTable({
  subject,
  nameHeader,
  period,
  size = DEFAULT_SIZE,
}: {
  subject: LeaderboardSubject;
  nameHeader: string;
  period: LeaderboardPeriodKey;
  size?: number;
}) {
  const hook = subject === 'USER' ? useUserLeaderboard : useClanLeaderboard;
  const { data, isFetching, isLoading } = hook({ key: period, size });
  const rows = (data?.entries ?? []) as LeaderboardEntryView[];

  const columns: Column<LeaderboardEntryView>[] = [
    { key: 'rank', header: '', width: 'w-10', render: (r) => r.rank },
    {
      key: 'name',
      header: nameHeader,
      render: (r) => <span className="font-medium">{r.name}</span>,
    },
    {
      key: 'paints',
      header: 'Pixels painted',
      align: 'right',
      render: (r) => r.paints.toLocaleString(),
    },
  ];

  return (
    <>
      <LoadingNotice isFetching={isFetching} />
      <LiteTable
        rows={rows}
        columns={columns}
        rowKey={(r) => r.id}
        emptyText={emptyText(isLoading)}
      />
    </>
  );
}

export function PlayersView({ period, size = DEFAULT_SIZE }: LeaderboardViewProps) {
  return <LeaderboardTable subject="USER" nameHeader="Player" period={period} size={size} />;
}

export function ClansView({ period, size = DEFAULT_SIZE }: LeaderboardViewProps) {
  return <LeaderboardTable subject="CLAN" nameHeader="Clan" period={period} size={size} />;
}
