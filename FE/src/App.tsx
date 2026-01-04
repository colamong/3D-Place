import { useEffect, useRef } from 'react';
import { Routes, Route, useLocation, matchPath } from 'react-router-dom';
import MainPage from '@/pages/MainPage';
import AdminPage from '@/pages/AdminPage';
import AcceptModal from '@/pages/AcceptModal';
import InfoModal from '@/pages/InfoModal';
import LeaderBoardModal from '@/pages/LeaderBoardModal';
import MyPageModal from '@/pages/MyPageModal';
import PaletteModal from '@/pages/PaletteModal';
import ClanModal from '@/pages/ClanModal';
import JoinPage from '@/pages/Join';
import EditProfileModal from '@/components/mypage/EditProfileModal';
import DeleteAccountModal from './components/mypage/DeleteAccountModal';
// TEMP dev page (easy to remove): LOD GLB test
import LodGlbTest from '@/pages/LodGlbTest';

function App() {
  const location = useLocation();
  const state = location.state as { backgroundLocation?: Location } | undefined;
  const backgroundRef = useRef<Location | null>(null);

  const modalPatterns = [
    '/accept',
    '/info',
    '/leaderboard',
    '/mypage',
    '/mypage/:id',
    '/mypage/:id/delete',
    '/palette',
    '/clan',
    '/clan/:id/editDesc',
    '/clan/:id/invite',
    '/clan/:id/members',
  ];

  const isModalPath = (pathname: string) =>
    modalPatterns.some((pattern) =>
      matchPath({ path: pattern, end: true }, pathname),
    );

  useEffect(() => {
    if (state?.backgroundLocation) {
      backgroundRef.current = state.backgroundLocation;
      return;
    }
    if (!isModalPath(location.pathname)) {
      backgroundRef.current = null;
    }
  }, [location, state]);

  const background = backgroundRef.current || state?.backgroundLocation;

  return (
    <>
      {/* 1) 기본 라우팅: 배경(또는 일반 접근 시) */}
      <Routes location={background || location}>
        <Route path="/" element={<MainPage />} />
        <Route path="/admin" element={<AdminPage />} />
        {import.meta.env.DEV && (
          <Route path="/_dev/lod" element={<LodGlbTest />} />
        )}

        {/* 사용자가 /palette 를 새로고침/직접접속하면 전체 페이지로 보여줌 */}
        <Route path="/accept" element={<AcceptModal />} />
        <Route path="/info" element={<InfoModal />} />
        <Route path="/leaderboard" element={<LeaderBoardModal />} />
        <Route path="/mypage" element={<MyPageModal />} />
        <Route path="/mypage/:id" element={<EditProfileModal />}></Route>
        <Route
          path="/mypage/:id/delete"
          element={<DeleteAccountModal />}
        ></Route>
        <Route path="/palette" element={<PaletteModal />} />
        <Route path="/clan/*" element={<ClanModal />}></Route>
        <Route path="/join" element={<JoinPage />} />
      </Routes>

      {/* 2) 모달 오버레이: 배경이 있을 때만 겹쳐 렌더 */}
      {background && isModalPath(location.pathname) && (
        <Routes>
          <Route path="/accept" element={<AcceptModal />} />
          <Route path="/info" element={<InfoModal />} />
          <Route path="/leaderboard" element={<LeaderBoardModal />} />
          <Route path="/mypage" element={<MyPageModal />} />
          <Route path="/mypage/:id" element={<EditProfileModal />}></Route>
          <Route
            path="/mypage/:id/delete"
            element={<DeleteAccountModal />}
          ></Route>
          <Route path="/palette" element={<PaletteModal />} />
          <Route path="/clan/*" element={<ClanModal />}></Route>
          {/* fallback to avoid console warnings when no route matches */}
          <Route path="*" element={null} />
        </Routes>
      )}
    </>
  );
}

export default App;
