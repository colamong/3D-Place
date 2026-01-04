import { useLocation, useNavigate } from 'react-router-dom';
import { useAnchor } from '@/stores/useAnchorStore';
import DockPopover from '@/components/common/DockPopover';
import ModalShell from '@/components/common/ModalShell';
import RoundBtn from '@/components/common/RoundBtn';
import { Pencil } from 'lucide-react';
import { useLogout } from '@/features/auth/queries/query';
import { useMeQuery } from '@/features/user/queries/query';
import { useUserLeaderboard } from '@/features/leaderboard/queries/query';

export default function MyPageModal() {
  const location = useLocation();
  const navigate = useNavigate();
  const bg = {
    backgroundLocation: (location.state as any)?.backgroundLocation ?? location,
  };
  const anchorRef = useAnchor('mypage'); // 전역에서 ref 구독

  // 로그아웃 로직 (게이트웨이 /api/logout 이동)
  const { mutate: logout, isPending } = useLogout();

  // 내 프로필 조회 (공통 응답)
  const { data: me, isLoading } = useMeQuery();
  const profile = me?.data;
  const nickname = profile?.nickname ?? profile?.email ?? 'User';
  const leaderboardSize = 100;
  const { data: leaderboard } = useUserLeaderboard({
    size: leaderboardSize,
  });
  const leaderboardEntries = leaderboard?.entries ?? [];
  const ownEntry = profile?.userId
    ? leaderboardEntries.find((entry) => entry.id === profile.userId)
    : undefined;
  const paintCount = ownEntry?.paints ?? profile?.paintCountTotal;
  const handle =
    profile?.nicknameHandle ??
    (profile?.nicknameSeq ? `#${profile.nicknameSeq}` : '');

  const profileMetadata = (profile as any)?.metadata;
  const avatarUrl =
    profileMetadata?.profileImage?.url ??
    profileMetadata?.avatarUrl ??
    profileMetadata?.avartarUrl ??
    (profile as any)?.avatarUrl;

  const content = (
    <div className="w-full">
      <header className="flex items-center gap-3 mb-3">
        <div className="relative w-15 h-15">
          <div className="w-full h-full rounded-full bg-gray-200 overflow-hidden">
            {avatarUrl && (
              <img
                src={avatarUrl}
                alt="avatar"
                className="w-full h-full object-cover"
                referrerPolicy="no-referrer"
              />
            )}
          </div>
          <RoundBtn
            className="absolute -bottom-1 -right-1 w-9 h-9 z-10 shadow-md"
            onClick={() => navigate('/mypage/me', { state: bg })}
            aria-label="Edit profile"
          >
            <Pencil size={16} />
          </RoundBtn>
        </div>
        <div className="flex flex-col">
          <div className="flex flex-col font-semibold">
            <span className="">{isLoading ? '…' : nickname}</span>
            <span className="text-teal-600 ml-1">
              {isLoading ? '' : `#${handle}`}
            </span>
          </div>
          <div className="text-sm text-gray-500">
            Pixels painted: {paintCount}
          </div>
        </div>
      </header>
      <div className="space-y-3">
        <button
          className="w-full h-12 rounded-full bg-gray-100 hover:bg-gray-200 text-left px-4 cursor-pointer"
          onClick={() => logout()}
          disabled={isPending}
        >
          {isPending ? 'Logging out…' : '↩︎ Log Out'}
        </button>
      </div>
    </div>
  );

  // 앵커가 있으면 팝오버, 없으면 중앙 모달
  if (anchorRef?.current) {
    return (
      <DockPopover
        anchorRef={anchorRef}
        placement="left"
        offset={12}
        onClose={() => navigate(-1)}
        className="w-[340px] max-h-[70vh] mt-8"
        withArrow={false}
      >
        {content}
      </DockPopover>
    );
  }
  return (
    <ModalShell onClose={() => navigate(-1)} className="w-[420px] h-auto">
      {content}
    </ModalShell>
  );
}
