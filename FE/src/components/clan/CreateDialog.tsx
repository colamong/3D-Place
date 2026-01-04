import { useCreateClanMutation } from '@/features/clan/queries/query';
import { cn } from '@/lib/cn';
import { useState } from 'react';

export function CreateDialog({
  onCancel,
  onCreated,
}: {
  onCancel: () => void;
  onCreated: (c: {
    id: string;
    name: string;
    members: number;
    pixelsPainted: number;
  }) => void;
}) {
  const { mutateAsync } = useCreateClanMutation();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [policy, setPolicy] = useState<'OPEN' | 'APPROVAL' | 'INVITE_ONLY'>(
    'OPEN',
  );
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const canSubmit =
    name.trim().length >= 2 && description.trim().length >= 0 && !busy;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setErr(null);
    try {
      const res = await mutateAsync({
        name: name.trim(),
        description: description.trim(),
        policy,
      });
      const d = res.data;
      onCreated({
        id: d.id,
        name: d.name,
        members: d.memberCount,
        pixelsPainted: d.paintCountTotal,
      });
    } catch (e) {
      setErr('Failed to create clan. Please rename and try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/30"
      aria-modal="true"
      role="dialog"
    >
      <form
        onSubmit={submit}
        className="w-[560px] rounded-3xl bg-white p-6 shadow-2xl"
      >
        <h3 className="mb-4 text-xl text-gray-700 font-semibold">
          Create Clan
        </h3>
        <main className="flex flex-col gap-2">
          <div className="flex gap-12 items-center">
            <span className="text-gray-700">Name</span>
            {/* UI留?留욎텣 ?꾨뱶 (?숈옉? ?숈씪 ?대쫫 ?ъ슜) */}
            <input
              placeholder="Input Alliance Name"
              className="flex-1 rounded-lg border px-4 py-2 outline-none focus:ring"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="flex gap-7 items-start">
            <span className="text-gray-700 w-16">Description</span>
            <textarea
              placeholder="Introduce your clan"
              className="flex-1 rounded-lg border px-4 py-2 outline-none focus:ring h-28"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
        </main>

        {err && (
          <p className="mt-3 text-sm text-red-600" role="alert">
            {err}
          </p>
        )}

        <div className="mt-5 flex justify-between items-center gap-2">
          <div className="flex gap-3 items-center">
            <span className="text-gray-700">Policy</span>
            <div
              role="radiogroup"
              aria-label="Join policy"
              className="flex gap-1"
            >
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
                    'rounded-full border px-4 py-2 text-sm cursor-pointer transition-colors',
                    policy === opt.key
                      ? 'bg-blue-600 border-blue-600 text-white shadow-sm'
                      : 'bg-white border-gray-300 text-gray-700 hover:bg-gray-50',
                  )}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          <div className="flex gap-2">
            <button
              type="button"
              className="rounded-full px-4 py-2 hover:bg-gray-100 cursor-pointer"
              onClick={onCancel}
              disabled={busy}
            >
              Cancel
            </button>
            <button
              type="submit"
              className={cn(
                'rounded-full bg-blue-600 px-5 py-2 font-semibold text-white cursor-pointer hover:bg-blue-700',
                !canSubmit && 'opacity-50 cursor-none',
              )}
              disabled={!canSubmit}
            >
              {busy ? 'Creating??' : 'Create'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
