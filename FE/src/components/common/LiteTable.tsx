import { cn } from '@/lib/cn';
import React from 'react';

export type Column<T> = {
  key: string;
  header: string;
  width?: string;
  align?: 'left' | 'right';
  render: (row: T) => React.ReactNode;
};

type Props<T> = {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => React.Key;
  emptyText?: string;
};

export default function LiteTable<T>({
  rows,
  columns,
  rowKey,
  emptyText = 'No data',
}: Props<T>) {
  if (!rows?.length) {
    return <div className="py-16 text-center text-gray-400">{emptyText}</div>;
  }

  return (
    <div className="grid grid-cols-[auto_1fr_auto]">
      {/* header */}
      <div className="col-span-3 grid grid-cols-subgrid text-lg font-semibold border-b py-3">
        {columns.map((c) => (
          <div
            key={c.key}
            className={cn(c.width, c.align === 'right' && 'text-right')}
          >
            {c.header}
          </div>
        ))}
      </div>

      {/* rows */}
      {rows.map((r) => (
        <div
          key={rowKey(r)}
          className="col-span-3 grid grid-cols-subgrid items-center border-b py-3"
        >
          {columns.map((c) => (
            <div
              key={c.key}
              className={cn(c.width, c.align === 'right' && 'text-right')}
            >
              {c.render(r)}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
