import { buildZip } from './zip.js';

/** Turns Firestore values into plain JSON-friendly ones (Timestamps become ISO-8601 text). */
export function plain(value: unknown): unknown {
  if (value === null || value === undefined) return value;
  if (typeof value === 'object') {
    const v = value as { toDate?: () => Date; _seconds?: number; _nanoseconds?: number };
    if (typeof v.toDate === 'function') return v.toDate().toISOString();
    if (typeof v._seconds === 'number') return new Date(v._seconds * 1000).toISOString();
    if (Array.isArray(value)) return value.map(plain);
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([k, x]) => [k, plain(x)]));
  }
  return value;
}

/**
 * One CSV cell. Quotes, commas and line breaks are escaped (RFC 4180), and a cell that would be read as a
 * formula by a spreadsheet (starts with = + - @ tab or CR) gets a leading apostrophe so opening the file
 * in Excel or Sheets can never run anything.
 */
export function csvCell(value: unknown): string {
  let text = value === null || value === undefined ? '' : typeof value === 'object' ? JSON.stringify(plain(value)) : String(value);
  if (/^[=+\-@\t\r]/.test(text) && !/^-?\d+(\.\d+)?$/.test(text)) text = `'${text}`;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(rows: Record<string, unknown>[], columns: string[]): string {
  const lines = [columns.map(csvCell).join(',')];
  for (const row of rows) lines.push(columns.map(c => csvCell(plain(row[c]))).join(','));
  return lines.join('\r\n') + '\r\n';
}

interface CsvSpec { file: string; collection: string; columns: string[] }
/** The readings people actually open in a spreadsheet. Everything is also in data.json. */
export const CSV_SPECS: CsvSpec[] = [
  { file: 'blood_sugar.csv', collection: 'glucoseReadings', columns: ['measuredAt', 'value', 'unit', 'readingType', 'source', 'createdAt', 'id'] },
  { file: 'blood_pressure.csv', collection: 'bpReadings', columns: ['measuredAt', 'systolic', 'diastolic', 'pulse', 'context', 'source', 'createdAt', 'id'] },
  { file: 'weight.csv', collection: 'weightLogs', columns: ['measuredAt', 'valueKg', 'weightKg', 'source', 'createdAt', 'id'] },
  { file: 'sleep.csv', collection: 'sleepLogs', columns: ['sleepStartAt', 'sleepEndAt', 'durationMinutes', 'measuredAt', 'source', 'createdAt', 'id'] },
  { file: 'activity.csv', collection: 'walkLogs', columns: ['measuredAt', 'minutes', 'seconds', 'steps', 'activityType', 'source', 'createdAt', 'id'] },
  { file: 'medication.csv', collection: 'medicationLogs', columns: ['measuredAt', 'name', 'taken', 'source', 'createdAt', 'id'] },
];

export function buildReadme(meta: { exportedAt: string; counts: Record<string, number> }): string {
  const lines = Object.entries(meta.counts).filter(([, n]) => n > 0).map(([name, n]) => `  - ${name}: ${n}`);
  return [
    'Nirog Bhumi - your data export',
    `Created: ${meta.exportedAt}`,
    '',
    'What is in this file',
    '  data.json   Everything we hold about you that is linked to your account, in one file.',
    '  *.csv       Your readings as spreadsheets (blood sugar, blood pressure, weight, sleep, activity, medication).',
    '  README.txt  This note.',
    '',
    'How many records of each kind are included',
    ...(lines.length ? lines : ['  (no records)']),
    '',
    'Notes',
    '  - Times are in UTC (ISO 8601). Blood sugar is in mg/dL unless a row says %, which is HbA1c.',
    '  - "source" says where a record came from: manual (you typed it), health_connect (your phone or watch), device, admin_correction, migration.',
    '  - This file contains health information. Keep it private and share it only with people you trust.',
    '  - The download link that brought you here stops working after a short time; ask for a new export in the app any time.',
    '',
  ].join('\r\n');
}

export function buildExportZip(exported: Record<string, unknown>, now: Date = new Date()): { zip: Buffer; counts: Record<string, number> } {
  const counts: Record<string, number> = {};
  for (const [key, value] of Object.entries(exported)) if (Array.isArray(value)) counts[key] = value.length;
  const files = [
    { name: 'data.json', data: JSON.stringify(plain(exported), null, 2) },
    ...CSV_SPECS.map(spec => ({ name: spec.file, data: toCsv((exported[spec.collection] as Record<string, unknown>[] | undefined) ?? [], spec.columns) })),
    { name: 'README.txt', data: buildReadme({ exportedAt: String(exported.exportedAt ?? now.toISOString()), counts }) },
  ];
  return { zip: buildZip(files, now), counts };
}
