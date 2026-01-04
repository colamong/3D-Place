import React, { useMemo, useState } from 'react';
import ModalShell from '../common/ModalShell';
import { TriangleAlert } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useDeleteAccountMutation, useMeQuery } from '@/features/user/queries/query';
import { useLogout } from '@/features/auth/queries/query';

const DeleteAccountModal = () => {
  const navigate = useNavigate();
  const [removing, setRemoving] = useState(false);
  const { data: me } = useMeQuery();
  const deleteAccount = useDeleteAccountMutation();
  const logout = useLogout();
  
  const username = useMemo(
    () => me?.data?.nickname ?? me?.data?.email ?? 'User',
    [me],
  );
  const [confirm, setConfirm] = useState('');

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setRemoving(true);
      // TODO: 계정 삭제 API 연결 (백엔드 준비 시 교체)
      // 임시 대기 및 닫기
      await deleteAccount.mutateAsync();
      await logout.mutateAsync();
    } finally {
      setRemoving(false);
    }
  };

  return (
    <ModalShell>
      {/* 헤더부 */}
      <header className="flex gap-3 items-center">
        <TriangleAlert color="red" />
        <span className="font-extrabold text-2xl">DeleteAccount</span>
      </header>
      {/* 본문 */}
      <main className="flex flex-col gap-2 text-xl my-4">
        <span>
          Are you absolutely sure? This will permanently delete your account and
          all associated data. This action cannot be undone.
        </span>
        <span>
          This action is irreversible, do you want to proceed? Please confirm by
          entering your username:
        </span>
      </main>
      <form className="space-y-4" onSubmit={onSubmit}>
        <section className="flex flex-col gap-3">
          <div className="flex w-full rounded-3xl border-2 border-[#D9D9D9] h-12 justify-center items-center">
            <span className="text-xl">{username}</span>
          </div>
          <input
            type="text"
            placeholder="Type your username"
            className="w-full rounded-3xl border-3 border-[#D9D9D9] pl-3 h-12"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </section>
        {/* 버튼영역 */}
        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="h-10 px-4 rounded-lg bg-gray-100 hover:bg-gray-200 cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={removing || confirm.trim() !== username}
            className="h-10 px-4 rounded-lg bg-red-600 text-white hover:bg-red-700 disabled:opacity-60 cursor-pointer"
          >
            {removing ? 'Removing...' : 'Remove'}
          </button>
        </div>
      </form>
    </ModalShell>
  );
};

export default DeleteAccountModal;
