import React, { useMemo } from 'react';
import { Activity, Settings } from 'lucide-react';
import { Badge } from '@/components/admin/ui/badge';
import { Button } from '@/components/admin/ui/Button';
import { Card, CardContent } from '@/components/admin/ui/card';

import { ScopeTabs } from '@/components/admin/ScopeTabs';
import { KpiGrid } from '@/components/admin/KpiGrid';
import { ModuleHealthTable } from '@/components/admin/ModuleHealthTable';
import {
  ConsumerTimeSeries,
  ConsumerSnapshotPie,
} from '@/components/admin/ConsumerCharts';
import { DlqPanel } from '@/components/admin/DlqPanel';
import { UsersTable } from '@/components/admin/UsersTable';
import { ReportsTable } from '@/components/admin/ReportsTable';

import { useOpsDashboard } from '@/features/admin/queries/query';
import { useAdminStore } from '@/stores/useAdminStore';

export default function TplaceOpsDashboardContainer() {
  // 전체 페이로드 폴링(‘all’ 고정)
  const { isFetching, isError, refetch } = useOpsDashboard('all', {
    refetchIntervalMs: 30_000,
    staleTimeMs: 5_000,
  });

  // 전역 상태에서 scope/payload만 구독
  const scope = useAdminStore((s) => s.scope);
  const payload = useAdminStore((s) => s.payload);

  // 파생 데이터는 컴포넌트에서 계산(useMemo)
  const kpis = useMemo(() => {
    if (!payload?.kpis) return [];
    return payload.kpis[scope] ?? [];
  }, [payload, scope]);

  const rows = useMemo(() => {
    const all = payload?.moduleHealth ?? [];
    return scope === 'all' ? all : all.filter((r) => r.module === scope);
  }, [payload, scope]);

  const series = payload?.consumerRateSeries ?? [];
  const snapshot = payload?.consumerSnapshot ?? [];
  const dlq = payload?.dlq ?? [];
  const users = payload?.users ?? [];
  const reports = payload?.reports ?? [];

  const isEmpty = !payload?.kpis || !payload.kpis[scope]?.length;

  // 초기 로딩(첫 페치 중 + 데이터 비어있음)
  if (isFetching && isEmpty) {
    return (
      <div className="min-h-screen w-full bg-gray-50 p-6 min-w-0 [overflow-anchor:none]">
        <header className="mb-4 flex items-center gap-3">
          <Activity className="h-6 w-6" />
          <h1 className="text-2xl font-semibold">3D Place Ops Dashboard</h1>
          <Badge variant="secondary" className="ml-2">
            loading…
          </Badge>
        </header>
        <Card>
          <CardContent className="p-6">Fetching metrics…</CardContent>
        </Card>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="min-h-screen w-full bg-gray-50 p-6">
        <header className="mb-4 flex items-center gap-3">
          <Activity className="h-6 w-6" />
          <h1 className="text-2xl font-semibold">3D Place Ops Dashboard</h1>
          <Badge variant="destructive" className="ml-2">
            failed
          </Badge>
          <Button
            variant="outline"
            size="sm"
            className="ml-auto"
            onClick={() => refetch()}
          >
            Retry
          </Button>
        </header>
      </div>
    );
  }

  return (
    <div className="min-h-screen w-full bg-gray-50 p-6 min-w-0">
      <header className="mb-4 flex items-center gap-3">
        <Activity className="h-6 w-6" />
        <h1 className="text-2xl font-semibold">3D Place Ops Dashboard</h1>
        <Badge variant={isFetching ? 'secondary' : 'outline'} className="ml-2">
          {isFetching ? 'syncing…' : 'live'}
        </Badge>
        <div className="ml-auto flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Refresh
          </Button>
          <Button size="sm">
            <Settings className="mr-1 h-4 w-4" />
            Configure
          </Button>
        </div>
      </header>

      {/* 탭: zustand 기반 */}
      <ScopeTabs />

      {/* KPI / 표 / 차트: payload 파생값 전달 */}
      <KpiGrid items={kpis} />

      <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="xl:col-span-3">
          <ModuleHealthTable rows={rows} scope={scope} />
        </div>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2 min-w-0">
          <ConsumerTimeSeries series={series} scope={scope} />
        </div>
        <div className="xl:col-span-1 min-w-0">
          <ConsumerSnapshotPie data={snapshot} scope={scope} />
        </div>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2 min-w-0">
          <DlqPanel items={dlq} defaultTopic={dlq[0]?.id} />
        </div>
        <div className="xl:col-span-1 min-w-0">
          <Card className="rounded-2xl shadow-sm">
            <CardContent className="p-4 text-sm text-muted-foreground">
              Add scope-specific charts here (gateway p95, ws conn, etc.) using
              data from API.
            </CardContent>
          </Card>
        </div>
      </div>

      <div className="mt-8 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <UsersTable users={users} />
        <ReportsTable reports={reports} />
      </div>

      <footer className="mt-8 flex items-center justify-between text-xs text-muted-foreground">
        <div>API-driven · TanStack Query · axios</div>
        <div>v1.0 • Componentized</div>
      </footer>

      {/*
  개발 디버그(필요시 잠깐 켜서 확인)
  <pre className="text-[10px] text-gray-500">
    {JSON.stringify({ scope, all: payload?.kpis?.all?.length ?? 0, scopeLen: payload?.kpis?.[scope]?.length ?? 0 })}
  </pre>
  */}
    </div>
  );
}
