import type {
  ModuleId,
  OpsDashboardPayload,
  KpiDatum,
  ModuleHealthRow,
  ConsumerRatePoint,
  ConsumerSnapshot,
  DlqItem,
  UserRow,
  ReportRow,
} from '@/types/admin';

const modules: Exclude<ModuleId, 'all'>[] = [
  'gateway',
  'ws',
  'paint',
  'world',
  'user',
  'report',
  'worker',
];

function deriveAllKpis(
  kpis: Record<Exclude<ModuleId, 'all'>, KpiDatum[]> & { all?: KpiDatum[] },
) {
  const all =
    kpis.all && kpis.all.length
      ? kpis.all
      : modules.flatMap((m) => kpis[m] ?? []);
  return { ...kpis, all };
}

export function mockOpsDashboard(): OpsDashboardPayload {
  const kpis = deriveAllKpis({
    gateway: [
      { label: 'Gateway RPS', value: 420, icon: () => null },
      { label: 'Kafka Lag', value: 15000, icon: () => null },
    ],
    ws: [{ label: 'WS Conn', value: 3200, icon: () => null }],
    paint: [{ label: 'p95 Latency (ms)', value: 120, icon: () => null }],
    world: [],
    user: [],
    report: [],
    worker: [],
    all: [], // 비워두면 자동 합성
  } as any);

  const moduleHealth: ModuleHealthRow[] = [
    {
      module: 'gateway',
      ver: '1.2.3',
      pods: 5,
      restarts: 0,
      status: 'healthy',
    },
    { module: 'ws', ver: '0.9.1', pods: 3, restarts: 1, status: 'warn' },
  ];

  const consumerRateSeries: ConsumerRatePoint[] = [
    {
      t: '12:00',
      gateway: 100,
      ws: 10,
      paint: 20,
      world: 5,
      user: 8,
      report: 2,
      worker: 3,
    },
    {
      t: '12:05',
      gateway: 120,
      ws: 12,
      paint: 18,
      world: 6,
      user: 7,
      report: 3,
      worker: 2,
    },
  ];

  const consumerSnapshot: ConsumerSnapshot = [
    { name: 'gateway', value: 55 },
    { name: 'ws', value: 20 },
    { name: 'paint', value: 10 },
    { name: 'worker', value: 5 },
  ];

  const dlq: DlqItem[] = [
    { id: 'topic.dlq.gateway', count: 12, last: '2025-10-29 13:43' },
  ];

  const users: UserRow[] = [
    {
      id: 'u_1',
      nickname: 'alice',
      email: 'alice@example.com',
      roles: ['user'],
      status: 'active',
      createdAt: '2025-10-01',
      lastSeen: '2025-10-29 13:10',
    },
    {
      id: 'u_2',
      nickname: 'bruce',
      email: 'bruce@example.com',
      roles: ['user'],
      status: 'active',
      createdAt: '2025-10-02',
      lastSeen: '2025-10-29 13:20',
    },
    {
      id: 'u_3',
      nickname: 'simpson',
      email: 'simpson@example.com',
      roles: ['user'],
      status: 'active',
      createdAt: '2025-10-03',
      lastSeen: '2025-10-29 13:30',
    },
  ];

  const reports: ReportRow[] = [
    {
      id: 'r_1',
      type: 'spam',
      reporter: 'u_1',
      targetUser: 'u_9',
      evidence: 'https://example.com/spam',
      status: 'open',
      createdAt: '2025-10-29 13:10',
    },
    {
      id: 'r_2',
      type: 'richam',
      reporter: 'u_2',
      targetUser: 'u_9',
      evidence: 'https://example.com/richam',
      status: 'open',
      createdAt: '2025-10-29 13:20',
    },
    {
      id: 'r_3',
      type: 'spam_classic',
      reporter: 'u_3',
      targetUser: 'u_9',
      evidence: 'https://example.com/spam_classic',
      status: 'open',
      createdAt: '2025-10-29 13:30',
    },
  ];

  return {
    kpis,
    moduleHealth,
    consumerRateSeries,
    consumerSnapshot,
    dlq,
    users,
    reports,
  };
}

export function mockOpsDashboardByScope(scope: ModuleId): OpsDashboardPayload {
  const base = mockOpsDashboard();
  if (scope === 'all') return base;
  return {
    ...base,
    // All 탭에서 해당 스코프 KPI만 보이게 하고 싶다면:
    kpis: { ...base.kpis, all: base.kpis[scope] ?? [] },
    moduleHealth: base.moduleHealth.filter((r) => r.module === scope),
    // series/snapshot은 전체 유지(필요시 scope 기반으로 파생해도 됨)
  };
}
