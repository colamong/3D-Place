import { create } from 'zustand';

import { devtools } from 'zustand/middleware';

import type { ModuleId, OpsDashboardPayload } from '@/types/admin';

type AdminState = {
  scope: ModuleId;
  setScope: (s: ModuleId) => void;

  payload: OpsDashboardPayload | null;
  setPayload: (p: OpsDashboardPayload) => void;
};

export const useAdminStore = create<AdminState>()(
  devtools(
    (set) => ({
      scope: 'all' as ModuleId,
      setScope: (s) => set({ scope: s }),

      payload: null,
      setPayload: (p) => set({ payload: p }),
    }),
    { name: 'admin-store' },
  ),
);
