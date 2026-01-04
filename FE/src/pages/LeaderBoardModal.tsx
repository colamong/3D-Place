import React, { useState } from 'react';
import ModalShell from '@/components/common/ModalShell';
import { UserRound, UsersRound, Medal } from 'lucide-react';
import { PlayersView, ClansView } from '@/components/leaderboard/LBViews';
import { cn } from '@/lib/cn';
import type { LeaderboardPeriodKey } from '@/api/types';

type Cat = 'players' | 'clans';

const PERIOD_OPTIONS: { id: LeaderboardPeriodKey; label: string }[] = [
  { id: 'DAY', label: 'Today' },
  { id: 'WEEK', label: 'Week' },
  { id: 'MONTH', label: 'Month' },
];

export default function LeaderBoardModal() {
  const [cat, setCat] = useState<Cat>('players');
  const [period, setPeriod] = useState<LeaderboardPeriodKey>('DAY');

  const onSelectCat = (next: Cat) => {
    setCat(next);
    setPeriod('DAY');
  };

  const Tab = ({
    id,
    icon,
    label,
  }: {
    id: Cat;
    icon: React.ReactNode;
    label: string;
  }) => (
    <button
      onClick={() => onSelectCat(id)}
      className={cn(
        'flex items-center gap-2 px-4 h-10 rounded-3xl w-1/2 justify-center cursor-pointer',
        cat === id ? 'bg-white text-black' : 'text-gray-500 hover:text-black',
      )}
    >
      {icon}
      <span className="font-medium">{label}</span>
    </button>
  );

  const PeriodBtn = ({ id, label }: { id: LeaderboardPeriodKey; label: string }) => (
    <button
      onClick={() => setPeriod(id)}
      className={cn(
        'pb-1 cursor-pointer',
        period === id
          ? 'font-semibold border-b-3 border-black'
          : 'text-gray-500',
      )}
    >
      {label}
    </button>
  );

  return (
    <ModalShell>
      <div className="h-full w-full flex flex-col overflow-hidden">
        <header className="flex gap-2 items-center mb-4 text-xl md:text-3xl font-extrabold">
          <Medal size={26} />
          <p>Leaderboard</p>
        </header>

        {/* 카테고리 탭 */}
        <div className="flex justify-between bg-gray-200 rounded-3xl p-1 mb-4">
          <Tab
            id="players"
            icon={<UserRound className="w-5 h-5" />}
            label="Players"
          />
          <Tab
            id="clans"
            icon={<UsersRound className="w-5 h-5" />}
            label="Clans"
          />
        </div>

        {/* 기간 선택 */}
        <div className="flex gap-6 mb-6">
          {PERIOD_OPTIONS.map((option) => (
            <PeriodBtn key={option.id} id={option.id} label={option.label} />
          ))}
        </div>

        {/* 리스트 (스크롤 영역) */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          {cat === 'players' && <PlayersView period={period} />}
          {cat === 'clans' && <ClansView period={period} />}
        </div>
      </div>
    </ModalShell>
  );
}

