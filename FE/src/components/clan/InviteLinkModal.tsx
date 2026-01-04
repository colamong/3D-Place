import React, { useEffect, useState } from 'react';
import ModalShell from '@/components/common/ModalShell';
import { useNavigate, useParams } from 'react-router-dom';
import { cn } from '@/lib/cn';
import { issueClanInviteTicket } from '@/api/clan';

export default function InviteLinkModal() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [inviteUrl, setInviteUrl] = useState('');
  const [copied, setCopied] = useState(false);

  const [portal, setPortal] = useState<HTMLElement | null>(null);
  useEffect(() => {
    setPortal(document.getElementById('clan-modal-content'));
  }, []);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const res = await issueClanInviteTicket(id!);
        const token = res?.data?.code;
        const configuredBase = (import.meta.env.VITE_FRONT_URL ?? '').trim();
        const base =
          configuredBase || window.location?.origin || 'http://localhost:5173';
        const normalizedBase = base.replace(/\/$/, '');
        const url = token
          ? `${normalizedBase}/join?token=${encodeURIComponent(token)}`
          : '';
        if (alive) setInviteUrl(url);
      } catch {
        if (alive) setInviteUrl('');
      }
    })();
    return () => {
      alive = false;
    };
  }, [id]);

  const copy = async () => {
    if (!inviteUrl) return;
    try {
      await navigator.clipboard.writeText(inviteUrl);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = inviteUrl;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    } finally {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <ModalShell
      onClose={() => navigate(-1)}
      className="w-[560px] h-auto"
      portalTarget={portal}
      backdropClassName="bg-black/10"
    >
      <div className="space-y-3">
        <h3 className="text-xl font-bold">Invite link</h3>
        <p className="text-gray-500">
          Send the link below to everybody you want to invite to the clan
        </p>
        <div className="flex items-center rounded-2xl border px-3 py-2 bg-gray-50">
          <div className="flex-1 truncate text-gray-700">
            {inviteUrl || 'Generating...'}
          </div>
          <button
            type="button"
            onClick={copy}
            disabled={!inviteUrl}
            className={cn(
              'ml-3 rounded-full bg-amber-500 px-4 py-2 font-semibold text-black disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed hover:opacity-90',
              copied && 'bg-green-500',
            )}
          >
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      </div>
    </ModalShell>
  );
}
