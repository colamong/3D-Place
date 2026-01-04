import React from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '@/components/admin/ui/card';
import { Button } from '@/components/admin/ui/Button';
import { Badge } from '@/components/admin/ui/badge';
import { Input } from '@/components/admin/ui/input';
import type { ReportRow } from '@/types/admin';
import { Search } from 'lucide-react';
import { type CsvHeader } from '@/lib/csv';
import { ExportCsvButton } from '@/components/admin/ExportCsvButton';
import { useFilterSort } from '@/features/admin/hooks/useFilterSort';

export function ReportsTable({ reports }: { reports: ReportRow[] }) {
  const headers: CsvHeader[] = [
    { key: 'id', label: 'ID' },
    { key: 'type', label: 'Type' },
    { key: 'reporter', label: 'Reporter' },
    { key: 'targetUser', label: 'Target' },
    { key: 'evidence', label: 'Evidence' },
    { key: 'status', label: 'Status' },
    { key: 'createdAt', label: 'Created At' },
  ];

  const {
    data: rowsView,
    query,
    setQuery,
    sortKey,
    setSortKey,
    sortDir,
    toggleSortDir,
  } = useFilterSort(reports, {
    queryBy: ['id', 'type', 'reporter', 'targetUser', 'evidence'],
    sortKeys: {
      id: (r) => r.id,
      type: (r) => r.type,
      reporter: (r) => r.reporter,
      targetUser: (r) => r.targetUser,
      status: (r) => r.status,
      createdAt: (r) => Date.parse(r.createdAt) || 0,
    },
    initialSortKey: 'createdAt',
    initialSortDir: 'desc',
  });

  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Reports</CardTitle>
        <div className="flex items-center gap-2">
          <Search size={36} />
          <select
            className="h-8 rounded-md border px-2 text-sm"
            value={sortKey}
            onChange={(e) => setSortKey(e.target.value)}
          >
            <option value="createdAt">Created At</option>{' '}
            <option value="id">ID</option> <option value="type">Type</option>{' '}
            <option value="reporter">Reporter</option>{' '}
            <option value="targetUser">Target</option>{' '}
            <option value="status">Status</option>{' '}
          </select>{' '}
          <Button size="sm" variant="outline" onClick={toggleSortDir}>
            {' '}
            {sortDir}{' '}
          </Button>
          <Input
            placeholder="filter type/status/user"
            className="h-8 w-56"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <ExportCsvButton
            filenamePrefix="reports"
            data={rowsView}
            headers={headers}
            map={(r) => ({
              id: r.id,
              type: r.type,
              reporter: r.reporter,
              targetUser: r.targetUser,
              evidence: r.evidence,
              status: r.status,
              createdAt: r.createdAt,
            })}
          />
        </div>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-muted-foreground">
              <tr>
                <th className="py-2 pr-3">ID</th>
                <th className="py-2 pr-3">Type</th>
                <th className="py-2 pr-3">Reporter</th>
                <th className="py-2 pr-3">Target</th>
                <th className="py-2 pr-3">Evidence</th>
                <th className="py-2 pr-3">Status</th>
                <th className="py-2 pr-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rowsView.map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="py-2 pr-3 font-mono">{r.id}</td>
                  <td className="py-2 pr-3">{r.type}</td>
                  <td className="py-2 pr-3 font-mono">{r.reporter}</td>
                  <td className="py-2 pr-3 font-mono">{r.targetUser}</td>
                  <td className="py-2 pr-3 truncate max-w-[180px]">
                    {r.evidence}
                  </td>
                  <td className="py-2 pr-3">
                    <Badge
                      variant={
                        r.status === 'open'
                          ? 'destructive'
                          : r.status === 'triage'
                            ? 'secondary'
                            : 'outline'
                      }
                    >
                      {r.status}
                    </Badge>
                  </td>
                  <td className="py-2 pr-0">
                    <div className="flex justify-end gap-2">
                      <Button size="sm" variant="outline">
                        Open
                      </Button>
                      <Button size="sm" variant="outline">
                        Assign
                      </Button>
                      <Button size="sm">Resolve</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="mt-2 text-xs text-muted-foreground">
          증거 링크는 프리사인드 URL로 열람 · 열람도 감사로그 기록
        </div>
      </CardContent>
    </Card>
  );
}
