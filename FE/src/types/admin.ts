export type ModuleId =
  | 'all'
  | 'gateway'
  | 'ws'
  | 'paint'
  | 'world'
  | 'user'
  | 'report'
  | 'worker';

export type KpiKey =
  | 'gatewayRps'
  | 'p95Ms'
  | 'wsConn'
  | 'kafkaLag'
  | 'redisHitPct'
  | 'pgPoolPct'
  | 'cf5xxPct';

export type KpiDatum = {
  label: string;
  value: number | string;
  icon: React.ComponentType<any>;
};
export type KpiResponse = Record<ModuleId, KpiDatum[]> & { all: KpiDatum[] };

export type ModuleHealthRow = {
  module: Exclude<ModuleId, 'all'>;
  ver: string;
  pods: number;
  restarts: number;
  status: 'healthy' | 'warn' | 'critical';
};

export type ConsumerRatePoint = {
  t: string; // HH:mm
  gateway: number;
  ws: number;
  paint: number;
  world: number;
  user: number;
  report: number;
  worker: number;
};

export type ConsumerSnapshot = {
  name: Exclude<ModuleId, 'all'>;
  value: number;
}[];

export type DlqItem = { id: string; count: number; last: string };
export type UserRow = {
  id: string;
  nickname: string;
  email: string;
  roles: string[];
  status: 'active' | 'muted' | 'banned';
  createdAt: string;
  lastSeen: string;
};
export type ReportRow = {
  id: string;
  type: string;
  reporter: string;
  targetUser: string;
  evidence: string;
  status: 'open' | 'triage' | 'closed';
  createdAt: string;
};

export type OpsDashboardPayload = {
  kpis: KpiResponse;
  moduleHealth: ModuleHealthRow[];
  consumerRateSeries: ConsumerRatePoint[];
  consumerSnapshot: ConsumerSnapshot;
  dlq: DlqItem[];
  users: UserRow[];
  reports: ReportRow[];
};
