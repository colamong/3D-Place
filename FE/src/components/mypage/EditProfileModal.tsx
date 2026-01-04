import { useEffect, useRef, useState } from 'react';
import { useAvatarPreview } from '@/features/admin/hooks/useAvatarPreview';
import ModalShell from '@/components/common/ModalShell';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Ellipsis, FlipHorizontal, Pencil, Plus } from 'lucide-react';
import DockPopover from '@/components/common/DockPopover';
import {
  useMeQuery,
  useUpdateProfileMutation,
  usePublicProfileQuery,
} from '@/features/user/queries/query';
import { uploadUserAvatarAndGetCdnUrl } from '@/api/uploads';
import { useLogoutAllDevicesMutation } from '@/features/auth/queries/query';

export default function EditProfileModal() {
  const navigate = useNavigate();
  const { id: rawId } = useParams<{ id: string }>();
  const location = useLocation();

  const { data: me } = useMeQuery();
  const myUserId =
    me?.data?.userId ??
    (me?.data as any)?.identities?.find((m: any) => m?.userId)?.userId ??
    null;
  const routeId = rawId ? decodeURIComponent(rawId) : 'me';
  const isSelf =
    !routeId || routeId === 'me' || (myUserId && routeId === myUserId);
  const { data: userDetail } = usePublicProfileQuery(routeId, {
    enabled: !isSelf && !!routeId,
  });
  const profile = isSelf ? me?.data : userDetail?.data;

  const update = useUpdateProfileMutation();

  // 폼 상태 (프로필 로드 전에도 기본값 노출)
  const [nickname, setNickname] = useState(
    profile?.nickname ?? profile?.email ?? 'User',
  );
  const [userImg, setUserImg] = useState<File | null>(null);
  const [avatarUploadStatus, setAvatarUploadStatus] = useState<
    'idle' | 'uploading' | 'success' | 'error'
  >('idle');
  const [uploadedAvatarUrl, setUploadedAvatarUrl] = useState<string | null>(
    null,
  );
  const previewUrl = useAvatarPreview(userImg, null);
  const avatarFromProfile =
    (profile as any)?.metadata?.avatarUrl ??
    (profile as any)?.metadata?.avartarUrl ??
    (profile as any)?.avatarUrl;
  const avatarSrc = previewUrl ?? avatarFromProfile ?? '';
  const [saving, setSaving] = useState(false); // deprecated, kept for compatibility (use update.isPending)
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const moreBtnRef = useRef<HTMLButtonElement | null>(null);
  const [moreOpen, setMoreOpen] = useState(false);
  const portalRef = useRef<HTMLDivElement | null>(null);
  const logoutAll = useLogoutAllDevicesMutation();
  const profileUserId =
    profile?.userId ??
    (profile as any)?.identities?.find((m: any) => m?.userId)?.userId ??
    (isSelf ? myUserId ?? undefined : undefined);
  const profileReady = Boolean(profileUserId);
  const profileKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (!profileReady) return;
    const nextKey = `${profileUserId ?? 'unknown'}:${routeId}`;
    if (profileKeyRef.current === nextKey) return;
    profileKeyRef.current = nextKey;
    setNickname(profile?.nickname ?? profile?.email ?? 'User');
    setUserImg(null);
    setAvatarUploadStatus('idle');
    setUploadedAvatarUrl(null);
  }, [
    profileReady,
    profileUserId,
    profile?.nickname,
    profile?.email,
    routeId,
    profile,
  ]);

  const originalNickname = profile?.nickname ?? '';
  const avatarChanged = !!userImg; // 파일이 선택되면 아바타 변경으로 간주
  const canSave =
    !update.isPending &&
    avatarUploadStatus !== 'uploading' &&
    nickname.trim().length > 0 &&
    (nickname !== originalNickname || avatarChanged) &&
    (!avatarChanged || avatarUploadStatus === 'success');

  const handleAvatarFileChange = async (file: File | null) => {
    setUserImg(file);
    setUploadedAvatarUrl(null);

    if (!file) {
      setAvatarUploadStatus('idle');
      return;
    }
    if (!profileUserId) {
      setAvatarUploadStatus('error');
      console.warn('Cannot upload avatar without userId');
      return;
    }

    setAvatarUploadStatus('uploading');
    try {
      const cdnUrl = await uploadUserAvatarAndGetCdnUrl(
        file,
        String(profileUserId),
      );
      setUploadedAvatarUrl(cdnUrl);
      setAvatarUploadStatus('success');
      console.log('[avatar-upload] presign+commit success', { cdnUrl });
    } catch (err) {
      setAvatarUploadStatus('error');
      console.error('Avatar upload failed before save', err);
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSave) return;
    const body: any = { nickname };
    if (uploadedAvatarUrl) {
      body.metadata = {
        ...(profile as any)?.metadata,
        avatarUrl: uploadedAvatarUrl,
        avartarUrl: uploadedAvatarUrl,
      };
    }
    await update.mutateAsync(body);
    navigate(-1);
  };

  const handleDeleteAccount = () => {
    setMoreOpen(false);
    if (!routeId) return; // 안전 가드
    const bg = (location.state as any)?.backgroundLocation ?? location;
    navigate(`/mypage/${encodeURIComponent(routeId)}/delete`, {
      state: { backgroundLocation: bg },
    });
  };
  return (
    <ModalShell onClose={() => navigate(-1)} className="w-[560px]">
      <h2 className="text-xl font-bold mb-4">Edit Profile</h2>

      <div ref={portalRef} className="relative">
        <form className="space-y-4" onSubmit={onSubmit}>
          {/* 아바타 및 닉네임 입력 */}
          <div className="flex gap-3">
            {/* 아바타 업로드 */}
            <div className="relative w-16 h-16 rounded-full bg-gray-200">
              {avatarSrc && (
                <img
                  src={avatarSrc}
                  alt="avatar preview"
                  className="w-full h-full rounded-full object-cover "
                />
              )}
              {/* 이 부분 동적 props 필요 : img 태그의 previewUrl 이 실제 백엔드 서버에서 등록되어있는 S3 url로 교체 필요 */}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                disabled={!profileReady}
                onChange={(e) =>
                  handleAvatarFileChange(e.target.files?.[0] ?? null)
                }
              />
              <button
                type="button"
                aria-label="Change avatar"
                onClick={() => profileReady && fileInputRef.current?.click()}
                disabled={!profileReady}
                className="absolute bottom-0 right-0 w-7 h-7 rounded-full bg-white shadow flex items-center justify-center hover:bg-gray-50 cursor-pointer"
              >
                {profileReady ? (
                  previewUrl ? (
                    <Pencil size={16} className="text-gray-600" />
                  ) : (
                    <Plus size={16} className="text-gray-600" />
                  )
                ) : (
                  <Ellipsis size={14} />
                )}
              </button>
            </div>

            {/* 닉네임 */}
            <div className="flex items-center gap-3">
              <label className="block text-lg font-medium">Nickname</label>
              <input
                className="w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:ring-2 focus:ring-blue-400"
                placeholder="Enter nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                disabled={update.isPending}
              />
              {/* 이 부분 동적 props 필요 : value로 해놓은 nickname이 임시라서 실제 데이터 prop 명으로 변경 필요 */}
            </div>
          </div>
          {/* 액션 */}
          <div className="flex justify-between pt-2">
            {/* 좌측 */}
            <div className="flex">
              <button
                type="button"
                ref={moreBtnRef}
                onClick={() => setMoreOpen((v) => !v)}
                className="h-10 px-4 rounded-full bg-gray-100 hover:bg-gray-200 text-gray-700 shadow-sm cursor-pointer"
              >
                More
              </button>
              {moreOpen && (
                <DockPopover
                  anchorRef={moreBtnRef as React.RefObject<HTMLElement>}
                  onClose={() => setMoreOpen(false)}
                  placement="top"
                  offset={14}
                  portalTarget={portalRef.current ?? undefined}
                  className="p-0 w-60 -mt-9 -ml-2"
                  withArrow={false}
                >
                  <div>
                    <button
                      type="button"
                      className="w-full text-left px-3 py-2 rounded-lg text-red-600 hover:bg-red-50  cursor-pointer"
                      onClick={() => {
                        setMoreOpen(false);
                        logoutAll.mutate(undefined, {
                          onSuccess: () => {
                            window.location.assign('/');
                          },
                        });
                      }}
                    >
                      Log out from all devices
                    </button>
                    <button
                      type="button"
                      className="w-full text-left px-3 py-2 rounded-lg text-red-600 hover:bg-red-50  cursor-pointer"
                      onClick={() => {
                        handleDeleteAccount();
                        // TODO: 계정 삭제 흐름 연결 (확인 모달 등)
                      }}
                    >
                      Delete Account
                    </button>
                  </div>
                </DockPopover>
              )}
            </div>
            {/* 우측 */}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => navigate(-1)}
                className="h-10 px-4 rounded-lg bg-gray-100 hover:bg-gray-200 cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!canSave}
                className="h-10 px-4 rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60 cursor-pointer"
              >
                {avatarUploadStatus === 'uploading'
                  ? 'Uploading...'
                  : update.isPending
                    ? 'Saving...'
                    : 'Save'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </ModalShell>
  );
}
