import type React from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import DockPopover from '@/components/common/DockPopover';
import { EllipsisVertical } from 'lucide-react';

export function MemberRow({
  member,
  canManage,
  onBan,
  onKick,
  onPromote,
  onDemote,
  onMakeOwner,
  portalTarget,
}: {
  member: {
    id: string;
    nickname: string;
    nicknameHandle?: string;
    role: 'MASTER' | 'OFFICER' | 'MEMBER';
    isOwner?: boolean;
    avatarColor?: string;
  };
  canManage: boolean;
  onBan: (id: string) => void;
  onKick: (id: string) => void;
  onPromote: (id: string) => void;
  onDemote: (id: string) => void;
  onMakeOwner: (id: string) => void;
  portalTarget?: HTMLElement;
}) {
  const btnRef = useRef<HTMLButtonElement | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (member.role === 'MASTER') setOpen(false);
  }, [member.role]);

  const initials = useMemo(() => {
    const source = member.nicknameHandle ?? member.nickname;
    return source?.[0]?.toUpperCase() ?? '?';
  }, [member.nickname, member.nicknameHandle]);
  const isOwner = !!member.isOwner;
  const canBan = !isOwner;
  const showActions = canManage && canBan;

  return (
    <li className="flex items-center justify-between rounded-2xl px-3 py-3 hover:bg-gray-50">
      <div className="flex items-center gap-3">
        <div
          className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold"
          style={{ backgroundColor: member.avatarColor ?? '#64748b' }}
        >
          {initials}
        </div>
        <div className="flex flex-col">
          <div className="flex items-center gap-2 text-blue-600 font-semibold">
            {member.nicknameHandle || member.nickname}
            {member.role === 'MASTER' && (
              <span className="rounded-full bg-amber-200 text-amber-800 text-xs font-semibold px-2 py-0.5">
                MASTER
              </span>
            )}
            {member.role === 'OFFICER' && (
              <span className="rounded-full bg-indigo-200 text-indigo-800 text-xs font-semibold px-2 py-0.5">
                OFFICER
              </span>
            )}
          </div>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {isOwner ? (
          <span className="text-xs text-gray-400 select-none">Owner</span>
        ) : showActions ? (
          <>
            <button
              type="button"
              ref={btnRef}
              onClick={() => setOpen((v) => !v)}
              className="p-2 rounded-full hover:bg-gray-100 cursor-pointer"
              aria-haspopup="menu"
            >
              <EllipsisVertical />
            </button>
            {open && (
              <DockPopover
                anchorRef={btnRef as React.RefObject<HTMLElement>}
                onClose={() => setOpen(false)}
                portalTarget={portalTarget}
                placement="left"
                offset={10}
                className="w-52"
                withArrow={false}
              >
                {member.role === 'MEMBER' && (
                  <button
                    type="button"
                    className="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-100 cursor-pointer"
                    onClick={() => {
                      setOpen(false);
                      onPromote(member.id);
                    }}
                  >
                    Promote to officer
                  </button>
                )}
                {member.role === 'OFFICER' && (
                  <button
                    type="button"
                    className="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-100 cursor-pointer"
                    onClick={() => {
                      setOpen(false);
                      onDemote(member.id);
                    }}
                  >
                    Demote to member
                  </button>
                )}
                <button
                  type="button"
                  className="w-full text-left px-3 py-2 rounded-lg hover:bg-gray-100 cursor-pointer"
                  onClick={() => {
                    setOpen(false);
                    onMakeOwner(member.id);
                  }}
                  disabled={isOwner}
                >
                  {isOwner ? 'Current owner' : 'Make owner'}
                </button>
                {/* <button
                  type="button"
                  className="w-full text-left px-3 py-2 rounded-lg text-red-600 hover:bg-red-50 cursor-pointer"
                  onClick={() => {
                    setOpen(false);
                    onBan(member.id);
                  }}
                >
                  Ban user
                </button> */}
                <button
                  type="button"
                  className="w-full text-left px-3 py-2 rounded-lg text-red-600 hover:bg-red-50 cursor-pointer"
                  onClick={() => {
                    setOpen(false);
                    onKick(member.id);
                  }}
                >
                  Kick user
                </button>
              </DockPopover>
            )}
          </>
        ) : null}
      </div>
    </li>
  );
}

export function BannedRow({
  member,
  canManage,
  onUnban,
}: {
  member: { id: string; nickname: string; nicknameHandle?: string };
  canManage: boolean;
  onUnban: (id: string) => void;
}) {
  const initials = useMemo(
    () => (member.nicknameHandle || member.nickname)?.[0]?.toUpperCase() ?? '?',
    [member.nickname, member.nicknameHandle],
  );
  return (
    <li className="flex items-center justify-between rounded-2xl px-3 py-3 hover:bg-gray-50">
      <div className="flex items-center gap-3">
        <div className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold bg-gray-400">
          {initials}
        </div>
        <div className="flex flex-col">
          <div className="flex items-center gap-2 text-blue-600 font-semibold">
            {member.nicknameHandle || member.nickname}
          </div>
        </div>
      </div>
      {canManage ? (
        <button
          type="button"
          onClick={() => onUnban(member.id)}
          className="rounded-full px-4 py-1 text-sm font-semibold border border-gray-300 hover:bg-gray-100 cursor-pointer"
        >
          Unban
        </button>
      ) : null}
    </li>
  );
}

export function JoinRequestRow({
  userId,
  canManage,
  onApprove,
  onReject,
}: {
  userId: string;
  canManage: boolean;
  onApprove: (id: string) => void | Promise<void>;
  onReject: (id: string) => void | Promise<void>;
}) {
  const code = userId.slice(-6);
  const nickname = userId.slice(0, 8);
  return (
    <li className="flex items-center justify-between rounded-2xl px-3 py-3 hover:bg-gray-50">
      <div className="flex items-center gap-3">
        <div className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold bg-slate-500">
          {nickname[0]?.toUpperCase()}
        </div>
        <div className="flex flex-col">
          <div className="flex items-center gap-2 text-blue-600 font-semibold">
            {nickname}
            <span className="text-gray-500">#{code}</span>
          </div>
          <div className="text-xs text-gray-500">
            Request details are currently limited
          </div>
        </div>
      </div>
      {canManage ? (
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="rounded-full px-4 py-1 text-sm font-semibold border border-gray-300 hover:bg-gray-100 cursor-pointer"
            onClick={() => onApprove(userId)}
          >
            Approve
          </button>
          <button
            type="button"
            className="rounded-full px-4 py-1 text-sm font-semibold border border-gray-300 hover:bg-gray-100 cursor-pointer"
            onClick={() => onReject(userId)}
          >
            Reject
          </button>
        </div>
      ) : null}
    </li>
  );
}

export function ConfirmDialog({
  title,
  description,
  confirmText,
  cancelText,
  onConfirm,
  onCancel,
}: {
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
}) {
  return (
    <div className="fixed inset-0 z-[10000] flex items-center justify-center">
      <div className="absolute inset-0 bg-black/20" onClick={onCancel} />
      <div className="relative bg-white rounded-2xl shadow-xl p-6 w-[360px]">
        <h3 className="text-lg font-semibold mb-2">{title}</h3>
        {description && (
          <p className="text-sm text-gray-600 mb-4">{description}</p>
        )}
        <div className="flex justify-end gap-2">
          <button
            type="button"
            className="rounded-full px-4 py-1 text-sm font-semibold border border-gray-300 hover:bg-gray-100 cursor-pointer"
            onClick={onCancel}
          >
            {cancelText || 'Cancel'}
          </button>
          <button
            type="button"
            className="rounded-full px-4 py-1 text-sm font-semibold bg-blue-600 text-white hover:bg-blue-700 cursor-pointer"
            onClick={() => void onConfirm()}
          >
            {confirmText || 'Confirm'}
          </button>
        </div>
      </div>
    </div>
  );
}
