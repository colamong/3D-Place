export type CsvHeader = { key: string; label: string };

function escapeCell(v: unknown) {
  if (v === null || v === undefined) return '';
  const s = String(v).replace(/"/g, '""');
  // 콤마/따옴표/개행이 있으면 따옴표로 감쌈
  return /[",\n]/.test(s) ? '${s}' : s;
}

export function toCsv(rows: Array<Record<string, any>>, headers?: CsvHeader[]) {
  if (!rows?.length) return '';
  const keys = headers?.map((h) => h.key) ?? Object.keys(rows[0]);
  const head = (headers ?? keys.map((k) => ({ key: k, label: k })))
    .map((h) => escapeCell(h.label))
    .join(',');
  const body = rows
    .map((r) => keys.map((k) => escapeCell(r[k])).join(','))
    .join('\n');
  return `${head}\n${body}`;
}

export function downloadCsv(filename: string, csv: string) {
  const BOM = '\uFEFF'; // Excel 호환용
  const blob = new Blob([BOM + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function exportCsv(
  filename: string,
  rows: Array<Record<string, any>>,
  headers?: CsvHeader[],
) {
  const csv = toCsv(rows, headers);
  downloadCsv(filename, csv);
}
