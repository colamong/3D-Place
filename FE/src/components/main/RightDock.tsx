import { useEffect, useRef, useState } from 'react';
import type React from 'react';
import {
  Plus,
  Minus,
  UsersRound,
  Grid3x3,
  Map,
  LogIn,
  Compass,
  Pen,
  Pipette,
  MousePointerClick,
  SquareArrowOutUpLeft,
  LocateFixed,
  Medal,
  UserRoundPen,
  Ellipsis,
} from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAnchorStore } from '../../stores/useAnchorStore';
import RoundBtn from '../common/RoundBtn';
import { useLogin } from '@/features/user/queries/query';

type Props = {
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onToggleGrid?: () => void;
  gridOn?: boolean;
  gridDisabled?: boolean;
  onToggleMap?: () => void;
  mapOn?: boolean;
  onResetView?: () => void;
  onFlyToMyLocation?: () => void;
  modeDock?: {
    value: 'paint' | 'eyedropper' | 'select';
    onChange: (m: 'paint' | 'eyedropper' | 'select') => void;
  };
  onModePanelPlacementChange?: (p: 'overlay' | 'dock') => void;
  onDockOpenOverlay?: () => void; // 요청: 도크 → 오버레이 전환(펼친 상태로)
};

export default function RightDock({
  onZoomIn,
  onZoomOut,
  onToggleGrid,
  gridOn,
  gridDisabled,
  onToggleMap,
  mapOn,
  onResetView,
  onFlyToMyLocation,
  modeDock,
  onModePanelPlacementChange,
  onDockOpenOverlay,
}: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const bg = { backgroundLocation: location };

  // 로그인 상태/행동 훅 (호환 형태)
  const {
    mutate: login,
    isPending: loginPending,
    authenticated: isAuthed,
    isLoading: sessLoading,
  } = useLogin();

  const myRoundBtnRef = useRef<HTMLButtonElement>(null);
  const register = useAnchorStore((s) => s.register);
  const unregister = useAnchorStore((s) => s.unregister);

  useEffect(() => {
    register(
      'mypage',
      myRoundBtnRef as unknown as React.RefObject<HTMLElement>,
    );
    return () => unregister('mypage');
  }, [register, unregister]);

  const toMyPage = () => {
    if (location.pathname === '/mypage') {
      navigate(-1);
    } else {
      navigate('/mypage', { state: bg });
    }
  };

  const doLogin = () => {
    const next = location.pathname + location.search;
    login(next);
  };

  return (
    <>
      <div className="absolute right-6 top-10 flex flex-col gap-3 z-50 pointer-events-none">
        {sessLoading ? (
          <RoundBtn ref={myRoundBtnRef} disabled>
            <Ellipsis />
          </RoundBtn>
        ) : isAuthed ? (
          <RoundBtn ref={myRoundBtnRef} onClick={toMyPage}>
            <UserRoundPen />
          </RoundBtn>
        ) : (
          <RoundBtn
            ref={myRoundBtnRef}
            onClick={doLogin}
            disabled={loginPending}
            aria-busy={loginPending}
            aria-label="Login"
          >
            {loginPending ? '…' : <LogIn />}
          </RoundBtn>
        )}
        <RoundBtn onClick={() => navigate('/info', { state: bg })}>i</RoundBtn>
        <RoundBtn onClick={() => navigate('/leaderboard', { state: bg })}>
          <Medal />
        </RoundBtn>
        <RoundBtn onClick={() => navigate('/clan', { state: bg })}>
          <UsersRound />
        </RoundBtn>
        <RoundBtn onClick={onZoomIn}>
          <Plus />
        </RoundBtn>
        <RoundBtn onClick={onZoomOut}>
          <Minus />
        </RoundBtn>
        {/* Mode Selector 버튼 위치 */}
        {modeDock && (
          <DockModeCompact
            value={modeDock.value}
            onChange={modeDock.onChange}
            onOpenOverlay={() => {
              // 도크 → 오버레이 전환(펼침 상태로 시작)
              onModePanelPlacementChange?.('overlay');
              onDockOpenOverlay?.();
            }}
          />
        )}
      </div>
      <div className="absolute right-6 bottom-10 flex flex-col gap-3 z-50 pointer-events-none">
        <RoundBtn onClick={onFlyToMyLocation} title="내 위치로">
          <LocateFixed />
        </RoundBtn>
        <RoundBtn onClick={onResetView} title="카메라뷰">
          <Compass />
        </RoundBtn>
        <RoundBtn
          onClick={onToggleGrid}
          aria-pressed={gridOn}
          disabled={!!gridDisabled}
          title="격자"
        >
          <Grid3x3 />
        </RoundBtn>
        <RoundBtn
          onClick={onToggleMap}
          aria-pressed={!!mapOn}
          title={`지도 ${mapOn ? '끄기' : '켜기'}`}
        >
          <Map />
        </RoundBtn>
      </div>
    </>
  );
}

function DockModeCompact({
  value,
  onChange,
  onOpenOverlay,
}: {
  value: 'paint' | 'eyedropper' | 'select';
  onChange: (m: 'paint' | 'eyedropper' | 'select') => void;
  onOpenOverlay: () => void;
}) {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      if (!open) return;
      const el = wrapperRef.current;
      if (!el) return;
      if (!el.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick, true);
    return () => document.removeEventListener('mousedown', onDocClick, true);
  }, [open]);

  const icon =
    value === 'paint' ? (
      <Pen />
    ) : value === 'eyedropper' ? (
      <Pipette />
    ) : (
      <MousePointerClick />
    );

  return (
    <div ref={wrapperRef} className="relative pointer-events-auto">
      <RoundBtn onClick={() => setOpen((v) => !v)} title="Tools">
        {icon}
      </RoundBtn>
      {open && (
        <div
          className="absolute right-12 top-1/2 -translate-y-1/2 bg-white border border-black/5 rounded-xl shadow-lg px-2 py-2 flex items-center gap-2"
          style={{ zIndex: 60 }}
        >
          <RoundBtn
            aria-pressed={value === 'paint'}
            onClick={() => {
              onChange('paint');
              setOpen(false);
            }}
            title="Paint"
          >
            <Pen />
          </RoundBtn>
          <RoundBtn
            aria-pressed={value === 'select'}
            onClick={() => {
              onChange('select');
              setOpen(false);
            }}
            title="Select"
          >
            <MousePointerClick />
          </RoundBtn>
          <RoundBtn
            aria-pressed={value === 'eyedropper'}
            onClick={() => {
              onChange('eyedropper');
              setOpen(false);
            }}
            title="Pipette"
          >
            <Pipette />
          </RoundBtn>
          <div className="w-px h-6 bg-black/10" />
          <RoundBtn
            onClick={() => {
              setOpen(false);
              onOpenOverlay();
            }}
            title="Move to overlay"
          >
            <SquareArrowOutUpLeft />
          </RoundBtn>
        </div>
      )}
    </div>
  );
}
