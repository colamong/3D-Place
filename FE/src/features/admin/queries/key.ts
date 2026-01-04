// Query Keys (v5): scope별로 안정적인 키를 보장
import type { ModuleId } from '@/types/admin';

export const opsKeys = {
  root: ['ops'] as const,

  // 대시보드(스코프별)
  dashboard: (scope: ModuleId) =>
    [...opsKeys.root, 'dashboard', scope] as const,

  // DLQ 재처리 (뮤테이션 키 — invalidate 용도로만 사용)
  dlqReprocess: () => [...opsKeys.root, 'dlq', 'reprocess'] as const,

  // 전체 스코프 invalidation 용이하게: all scopes 리스트
  allDashboards: (scopes: ModuleId[]) =>
    scopes.map((s) => opsKeys.dashboard(s)),
};
