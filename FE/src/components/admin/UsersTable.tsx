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
import type { UserRow } from '@/types/admin';
import { type CsvHeader } from '@/lib/csv';
import { Search } from 'lucide-react';
import { ExportCsvButton } from './ExportCsvButton';
import { useFilterSort } from '@/features/admin/hooks/useFilterSort';

export function UsersTable({ users }: { users: UserRow[] }) {
  const headers: CsvHeader[] = [
    { key: 'id', label: 'ID' },
    { key: 'nickname', label: 'Nickname' },
    { key: 'email', label: 'Email' },
    { key: 'roles', label: 'Roles' },
    { key: 'status', label: 'Status' },
    { key: 'createdAt', label: 'Created At' },
    { key: 'lastSeen', label: 'Last Seen' },
  ];

  const {
    data: rowsView,
    query,
    setQuery,
    sortKey,
    setSortKey,
    sortDir,
    toggleSortDir,
  } = useFilterSort(users, {
    queryBy: ['id', 'nickname', 'email', (u) => u.roles.join(' ')],
    sortKeys: {
      id: (u) => u.id,
      nickname: (u) => u.nickname,
      email: (u) => u.email,
      status: (u) => u.status,
      createdAt: (u) => Date.parse(u.createdAt) || 0,
      lastSeen: (u) => Date.parse(u.lastSeen) || 0,
    },
    initialSortKey: 'id',
    initialSortDir: 'asc',
  });

  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Users</CardTitle>
        <div className="flex items-center gap-2">
          <Search size={36} />

          <select
            className="h-8 rounded-md border px-2 text-sm"
            value={sortKey}
            onChange={(e) => setSortKey(e.target.value)}
          >
            <option value="id">ID</option>{' '}
            <option value="nickname">Nickname</option>{' '}
            <option value="email">Email</option>{' '}
            <option value="status">Status</option>{' '}
            <option value="createdAt">Created At</option>{' '}
            <option value="lastSeen">Last Seen</option>
          </select>
          <Button size="sm" variant="outline" onClick={toggleSortDir}>
            {' '}
            {sortDir}{' '}
          </Button>
          <Input
            className="h-8 w-56"
            placeholder="search id/nickname/email"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <ExportCsvButton
            filenamePrefix="users"
            data={rowsView}
            headers={headers}
            map={(u) => ({
              id: u.id,
              nickname: u.nickname,
              email: u.email,
              roles: u.roles.join(', '),
              status: u.status,
              createdAt: u.createdAt,
              lastSeen: u.lastSeen,
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
                <th className="py-2 pr-3">Nickname</th>
                <th className="py-2 pr-3">Email</th>
                <th className="py-2 pr-3">Roles</th>
                <th className="py-2 pr-3">Status</th>
                <th className="py-2 pr-3">Last seen</th>
                <th className="py-2 pr-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rowsView.map((u) => (
                <tr key={u.id} className="border-t">
                  <td className="py-2 pr-3 font-mono">{u.id}</td>
                  <td className="py-2 pr-3">{u.nickname}</td>
                  <td className="py-2 pr-3">{u.email}</td>
                  <td className="py-2 pr-3">{u.roles.join(', ')}</td>
                  <td className="py-2 pr-3">
                    <Badge
                      variant={
                        u.status === 'active'
                          ? 'secondary'
                          : u.status === 'muted'
                            ? 'outline'
                            : 'destructive'
                      }
                    >
                      {u.status}
                    </Badge>
                  </td>
                  <td className="py-2 pr-3">{u.lastSeen}</td>
                  <td className="py-2 pr-0">
                    <div className="flex justify-end gap-2">
                      <Button size="sm" variant="outline">
                        View
                      </Button>
                      <Button size="sm" variant="outline">
                        Mute
                      </Button>
                      <Button size="sm" variant="destructive">
                        Ban
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="mt-2 text-xs text-muted-foreground">
          PII는 기본 마스킹 · 액션은 감사로그로 기록(RBAC: Operator/Admin)
        </div>
      </CardContent>
    </Card>
  );
}
