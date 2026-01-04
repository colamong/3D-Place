import React from 'react';
import { Card, CardContent } from '@/components/admin/ui/card';
import type { KpiDatum } from '@/types/admin';

export function KpiGrid({ items }: { items: KpiDatum[] }) {
  const riskColor = (label: string, val: number | string) => {
    if (label === 'Kafka Lag' && typeof val === 'number') {
      return val >= 50_000
        ? 'text-red-600'
        : val >= 10_000
          ? 'text-amber-600'
          : '';
    }
    return '';
  };
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-3 lg:grid-cols-7">
      {items.map((k) => (
        <Card key={k.label} className="rounded-2xl shadow-sm">
          <CardContent className="p-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              {k.icon ? <k.icon className="h-4 w-4" /> : null} {k.label}
            </div>
            <div
              className={`mt-2 text-xl font-semibold ${riskColor(k.label, k.value)}`}
            >
              {k.value}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
