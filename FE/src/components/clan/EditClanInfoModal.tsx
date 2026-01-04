import React, { useCallback, useEffect, useMemo, useState } from 'react';
import ModalShell from '../common/ModalShell';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  useClanDetailViewQuery,
  useUpdateClanMutation,
  useJoinRequestsQuery,
} from '@/features/clan/queries/query';
import { useMeQuery } from '@/features/user/queries/query';
import { cn } from '@/lib/cn';
import type { CommonResponse, ClanDetailView } from '@/api/types';

type EditMode = 'name' | 'description' | 'policy';

const EditClanInfoModal = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const state = (location.state as any) ?? {};
  const mode = (state.mode as EditMode) ?? 'description';
  const initialDesc = state.desc ?? '';
  const initialName = state.name ?? '';
  const initialPolicy = (state.policy || 'OPEN') as
    | 'OPEN'
    | 'APPROVAL'
    | 'INVITE_ONLY';
  const [policy, setPolicy] = useState<'OPEN' | 'APPROVAL' | 'INVITE_ONLY'>(
    initialPolicy,
  );
  const [description, setDescription] = useState(initialDesc);
  const [name, setName] = useState(initialName);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const { mutateAsync } = useUpdateClanMutation(id ?? '');
  const { data: detailRes, isLoading: detailLoading } = useClanDetailViewQuery(
    id ?? '',
  );
  const shouldCheckPending =
    mode === 'policy' && initialPolicy === 'APPROVAL' && Boolean(id);
  const { data: joinReqRes } = useJoinRequestsQuery(id ?? '', {
    enabled: shouldCheckPending,
  } as any);
  const { data: me, isLoading: meLoading } = useMeQuery();
  const clanDetail = detailRes as CommonResponse<ClanDetailView> | undefined;
  const members = (clanDetail?.data?.members ?? []) as Array<{
    userId: string;
    role: string;
  }>;
  const myUserId = me?.data?.userId;
  const myRole = useMemo(() => {
    if (!myUserId) return '';
    const mine = members.find((m) => m.userId === myUserId);
    return (mine?.role || '').toUpperCase();
  }, [members, myUserId]);
  const canEdit = myRole === 'MASTER' || myRole === 'OFFICER';
  const checkingPermission = detailLoading || meLoading;
  const unauthorized = !checkingPermission && (!canEdit || !id);

  const closeModal = useCallback(() => {
    navigate(-1);
  }, [navigate]);

  const closePolicyModal = useCallback(() => {
    if (id) {
      navigate(`/clan/${id}/members`, { replace: true });
    } else {
      closeModal();
    }
  }, [id, navigate]);

  const [portal, setPortal] = useState<HTMLElement | null>(null);
  useEffect(() => {
    setPortal(document.getElementById('clan-modal-content'));
  }, []);

  useEffect(() => {
    if (!shouldCheckPending) return;
    const response = joinReqRes as CommonResponse<string[]> | undefined;
    if (!response) return;
    const pending = (response.data ?? []).length;
    if (pending > 0) {
      window.alert(
        '요청된 join-request를 전부 처리 후 정책을 변경할 수 있습니다.',
      );
      closePolicyModal();
    }
  }, [joinReqRes, shouldCheckPending, closePolicyModal]);

  const titleMap: Record<EditMode, string> = {
    name: 'Edit clan name',
    description: 'Edit clan description',
    policy: 'Update join policy',
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!id) return closeModal();
    if (!canEdit) {
      setErr('클랜 정보를 수정할 권한이 없습니다.');
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      const payload: any = {};
      if (mode === 'name') {
        payload.name = name;
      } else if (mode === 'description') {
        payload.description = description;
      } else if (mode === 'policy') {
        payload.joinPolicy = policy;
      }
      await mutateAsync(payload);
      closeModal();
    } catch (_) {
      setErr(
        mode === 'name'
          ? 'Failed to create clan. Please rename and try again.'
          : '클랜 정보를 업데이트하는 중 오류가 발생했습니다.',
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <ModalShell
      onClose={() => closeModal()}
      className="w-[700px] h-auto"
      portalTarget={portal}
      backdropClassName="bg-black/10"
    >
      {checkingPermission ? (
        <div className="py-12 text-center text-gray-500">Loading...</div>
      ) : unauthorized ? (
        <div className="space-y-4 py-8 text-center">
          <p className="text-gray-700">클랜 정보를 수정할 권한이 없습니다.</p>
          <button
            type="button"
            onClick={() => closeModal()}
            className="px-5 py-2 rounded-3xl font-semibold bg-gray-700 hover:bg-gray-800 text-white cursor-pointer"
          >
            닫기
          </button>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4">
          <header className="text-2xl font-semibold mb-4">
            {titleMap[mode]}
          </header>
          <main className="flex flex-col gap-2">
            {mode === 'name' && (
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="border rounded-lg w-full px-3 py-2 outline-none focus:ring"
                placeholder={initialName ? '' : 'No name'}
              />
            )}
            {mode === 'description' && (
              <textarea
                placeholder={initialDesc ? '' : 'No description'}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="border rounded-lg w-full h-[180px] px-3 py-2 outline-none focus:ring"
              />
            )}
          </main>
          {err && (
            <p className="text-sm text-red-600" role="alert">
              {err}
            </p>
          )}

          {mode === 'policy' && (
            <div
              role="radiogroup"
              aria-label="Join policy"
              className="flex justify-between items-center gap-2"
            >
              <div className="flex gap-2">
                {(
                  [
                    { key: 'OPEN', label: 'Open' },
                    { key: 'APPROVAL', label: 'Approval' },
                    { key: 'INVITE_ONLY', label: 'Invite only' },
                  ] as const
                ).map((opt) => (
                  <button
                    key={opt.key}
                    type="button"
                    role="radio"
                    aria-checked={policy === opt.key}
                    onClick={() => setPolicy(opt.key)}
                    className={cn(
                      'rounded-full border px-5 py-2 text-sm cursor-pointer transition-colors',
                      policy === opt.key
                        ? 'bg-blue-600 border-blue-600 text-white shadow-sm'
                        : 'bg-white border-gray-300 text-gray-700 hover:bg-gray-50',
                    )}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="px-5 py-2 rounded-3xl font-semibold text-lg bg-gray-700 hover:bg-gray-800 text-white cursor-pointer"
                  onClick={() => closeModal()}
                  disabled={busy}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-3xl font-semibold bg-amber-500 hover:bg-amber-600 text-black cursor-pointer disabled:opacity-50"
                  disabled={busy}
                >
                  {busy ? 'Updating...' : 'Update'}
                </button>
              </div>
            </div>
          )}
          {mode !== 'policy' && (
            <div className="flex justify-end gap-2">
              <button
                type="button"
                className="px-5 py-2 rounded-3xl font-semibold text-lg bg-gray-700 hover:bg-gray-800 text-white cursor-pointer"
                onClick={() => closeModal()}
                disabled={busy}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 rounded-3xl font-semibold bg-amber-500 hover:bg-amber-600 text-black cursor-pointer disabled:opacity-50"
                disabled={busy}
              >
                {busy ? 'Updating...' : 'Update'}
              </button>
            </div>
          )}
        </form>
      )}
    </ModalShell>
  );
};

export default EditClanInfoModal;
