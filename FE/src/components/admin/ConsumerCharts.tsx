import React from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '@/components/admin/ui/card';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  CartesianGrid,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import type { ConsumerRatePoint, ConsumerSnapshot } from '@/types/admin';
import type { ModuleId } from '@/types/admin';
import { ChartContainer } from './ChartContainer';

const modules: Exclude<ModuleId, 'all'>[] = [
  'gateway',
  'ws',
  'paint',
  'world',
  'user',
  'report',
  'worker',
];

export function ConsumerTimeSeries({
  series,
  scope = 'all',
}: {
  series: ConsumerRatePoint[];
  scope?: ModuleId;
}) {
  const keys = scope === 'all' ? modules : [scope as Exclude<ModuleId, 'all'>];

  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader>
        <CardTitle>Kafka Consumer Rate by Module</CardTitle>
      </CardHeader>
      <CardContent className="h-64 min-w-0">
        <ChartContainer className="h-full min-w-0">
          <ResponsiveContainer width="100%" height="100%" minWidth={0}>
            <AreaChart data={series}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="t" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              {scope === 'all' && <Legend />}
              {keys.map((k) => (
                <Area
                  key={k}
                  type="monotone"
                  dataKey={k}
                  stackId={scope === 'all' ? '1' : undefined}
                  strokeWidth={2}
                  fillOpacity={0.15}
                />
              ))}
            </AreaChart>
          </ResponsiveContainer>
        </ChartContainer>
      </CardContent>
    </Card>
  );
}

export function ConsumerSnapshotPie({
  data,
  scope = 'all',
}: {
  data: ConsumerSnapshot;
  scope?: ModuleId;
}) {
  const pieData = scope === 'all' ? data : data.filter((d) => d.name === scope);

  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader>
        <CardTitle>Current Share (last min)</CardTitle>
      </CardHeader>
      <CardContent className="h-64 min-w-0">
        <ChartContainer className="h-full min-w-0">
          <ResponsiveContainer width="100%" height="100%" minWidth={0}>
            <PieChart>
              <Pie
                data={pieData}
                dataKey="value"
                nameKey="name"
                innerRadius={50}
                outerRadius={80}
                label
              >
                {pieData.map((_, i) => (
                  <Cell key={i} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </ChartContainer>
      </CardContent>
    </Card>
  );
}
