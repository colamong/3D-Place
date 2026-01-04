import React from 'react';
import { Button } from '@/components/admin/ui/Button';
import type { ModuleId } from '@/types/admin';
import { useAdminStore } from '@/stores/useAdminStore';

const modules: { id: ModuleId; name: string }[] = [
  { id: 'all', name: 'All' },
  { id: 'gateway', name: 'Gateway' },
  { id: 'ws', name: 'WS' },
  { id: 'paint', name: 'Paint' },
  { id: 'world', name: 'World' },
  { id: 'user', name: 'User' },
  { id: 'report', name: 'Report' },
  { id: 'worker', name: 'Worker' },
];

export function ScopeTabs() {
  const scope = useAdminStore((s) => s.scope);
  const setScope = useAdminStore((s) => s.setScope);

  return (
    <div className="mb-4 flex flex-wrap items-center gap-2">
      {modules.map((m) => (
        <Button
          key={m.id}
          size="sm"
          variant={scope === m.id ? 'default' : 'outline'}
          onClick={() => setScope(m.id)}
          className="rounded-full"
        >
          {m.name}
        </Button>
      ))}
    </div>
  );
}
