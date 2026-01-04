import React from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '@/components/admin/ui/card';
import { Badge } from '@/components/admin/ui/badge';
import type { ModuleHealthRow, ModuleId } from '@/types/admin';

export function ModuleHealthTable({
  rows,
  scope,
}: {
  rows: ModuleHealthRow[];
  scope: ModuleId;
}) {
  const data = scope === 'all' ? rows : rows.filter((r) => r.module === scope);
  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader>
        <CardTitle>Module Health</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-muted-foreground">
              <tr>
                <th className="py-2 pr-3">Module</th>
                <th className="py-2 pr-3">Version</th>
                <th className="py-2 pr-3">Pods</th>
                <th className="py-2 pr-3">Restarts</th>
                <th className="py-2 pr-3">Status</th>
              </tr>
            </thead>
            <tbody>
              {data.map((r) => (
                <tr key={r.module} className="border-t">
                  <td className="py-2 pr-3 capitalize">{r.module}</td>
                  <td className="py-2 pr-3 font-mono">{r.ver}</td>
                  <td className="py-2 pr-3">{r.pods}</td>
                  <td
                    className={`py-2 pr-3 ${r.restarts > 0 ? 'text-amber-600' : ''}`}
                  >
                    {r.restarts}
                  </td>
                  <td className="py-2 pr-3">
                    <Badge
                      variant={
                        r.status === 'healthy'
                          ? 'secondary'
                          : r.status === 'warn'
                            ? 'outline'
                            : 'destructive'
                      }
                    >
                      {r.status}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}
