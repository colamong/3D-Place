import React from 'react';

export type SortDir = 'asc' | 'desc';

type Accessor<T> = keyof T | ((item: T) => string);

type Config<T> = {
  queryBy: Accessor<T>[];
  sortKeys: Record<string, (item: T) => string | number>;
  initialSortKey?: string;
  initialSortDir?: SortDir;
};

export function useFilterSort<T>(items: T[], config: Config<T>) {
  const {
    queryBy,
    sortKeys,
    initialSortKey = Object.keys(sortKeys)[0],
    initialSortDir = 'asc',
  } = config;

  const [query, setQuery] = React.useState('');
  const deferredQuery = React.useDeferredValue(query);

  const [sortKey, setSortKey] = React.useState(initialSortKey);
  const [sortDir, setSortDir] = React.useState<SortDir>(initialSortDir);

  const data = React.useMemo(() => {
    const q = deferredQuery.trim().toLowerCase();

    const pick = (acc: Accessor<T>, item: T) =>
      typeof acc === 'function' ? acc(item) : String(item[acc] ?? '');

    const filtered = !q
      ? items
      : items.filter((it) =>
          queryBy.some((acc) => pick(acc, it).toLowerCase().includes(q)),
        );

    const getVal = sortKeys[sortKey] ?? (() => '');
    const sorted = filtered.slice().sort((a, b) => {
      const av = getVal(a);
      const bv = getVal(b);
      let cmp = 0;
      if (typeof av === 'number' && typeof bv === 'number') {
        cmp = av - bv;
      } else {
        cmp = String(av).localeCompare(String(bv));
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });

    return sorted;
  }, [items, deferredQuery, queryBy, sortKeys, sortKey, sortDir]);

  const toggleSortDir = () => setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));

  return {
    data,
    query,
    setQuery,
    sortKey,
    setSortKey,
    sortDir,
    setSortDir,
    toggleSortDir,
  };
}
