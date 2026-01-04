import React from 'react';
import { Button } from '@/components/admin/ui/Button';
import { exportCsv, type CsvHeader } from '@/lib/csv';

type Props<T> = {
  data: T[];
  headers: CsvHeader[];
  map: (item: T) => Record<string, any>;
  filenamePrefix: string; // 예: 'users', 'reports'
  label?: string; // 버튼 레이블(기본: 'Export CSV')
  className?: string;
  disabled?: boolean;
};

export function ExportCsvButton<T>({
  data,
  headers,
  map,
  filenamePrefix,
  label = 'Export CSV',
  className,
  disabled,
}: Props<T>) {
  const handleClick = () => {
    const rows = data.map(map);
    const date = new Date().toISOString().slice(0, 10);
    exportCsv(`${filenamePrefix}_${date}.csv`, rows, headers);
  };

  return (
    <Button
      size="md"
      variant="outline"
      onClick={handleClick}
      disabled={disabled || !data.length}
      className={className}
    >
      {label}
    </Button>
  );
}
