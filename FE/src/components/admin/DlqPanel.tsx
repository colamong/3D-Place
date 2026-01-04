import React, { useState } from 'react';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from '@/components/admin/ui/card';
import { Input } from '@/components/admin/ui/input';
import { Button } from '@/components/admin/ui/Button';
import { Badge } from '@/components/admin/ui/badge';
import { Separator } from '@/components/admin/ui/separator';
import { RefreshCw } from 'lucide-react';
import type { DlqItem } from '@/types/admin';
import { useDlqReprocess } from '@/features/admin/queries/query';

export function DlqPanel({
  items,
  defaultTopic,
}: {
  items: DlqItem[];
  defaultTopic?: string;
}) {
  const [topic, setTopic] = useState(defaultTopic ?? items[0]?.id ?? '');
  const { mutateAsync, isPending } = useDlqReprocess();

  return (
    <Card className="rounded-2xl shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>DLQ Reprocess</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <Input
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              className="h-8"
            />
            <Button
              size="sm"
              variant="outline"
              disabled={isPending}
              onClick={() => mutateAsync({ topic, dryRun: true })}
            >
              <RefreshCw className="mr-1 h-4 w-4" />
              Dry Run
            </Button>
            <Button
              size="sm"
              disabled={isPending}
              onClick={() => mutateAsync({ topic })}
            >
              Reprocess
            </Button>
          </div>
          <Separator />
          <div className="space-y-2">
            {items.map((d) => (
              <div
                key={d.id}
                className="flex items-center justify-between rounded-xl border p-3 text-sm"
              >
                <div>
                  <div className="font-medium">{d.id}</div>
                  <div className="text-xs text-muted-foreground">
                    last: {d.last}
                  </div>
                </div>
                <Badge variant="secondary">{d.count} msgs</Badge>
              </div>
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
