import { toDate, relativeTime } from './time'

/** Warm consistency read from a member's last check-in-ish timestamp. */
export function consistencyTag(lastActivityAt: unknown): {
  tag: string
  cls: string
  text: string
} {
  const date = toDate(lastActivityAt)
  if (!date) return { tag: 'Not started', cls: 'tag-neutral', text: 'No check-ins yet' }
  const days = (Date.now() - date.getTime()) / (24 * 60 * 60 * 1000)
  if (days <= 1.5) return { tag: 'Steady', cls: 'tag-good', text: `Checked in ${relativeTime(lastActivityAt)}` }
  if (days <= 4) return { tag: 'Easing off', cls: 'tag-warn', text: `Last check-in ${relativeTime(lastActivityAt)}` }
  return { tag: 'Quiet', cls: 'tag-bad', text: `Last check-in ${relativeTime(lastActivityAt)}` }
}

export type LogKind =
  | 'glucoseReadings'
  | 'bpReadings'
  | 'weightLogs'
  | 'sleepLogs'
  | 'walkLogs'
  | 'labReports'
  | 'checklistLogs'
  | 'dailyCheckins'

export const LOG_COLLECTIONS: { kind: LogKind; label: string; icon: string }[] = [
  { kind: 'glucoseReadings', label: 'Blood sugar', icon: '🩸' },
  { kind: 'bpReadings', label: 'Blood pressure', icon: '❤️' },
  { kind: 'weightLogs', label: 'Weight', icon: '⚖️' },
  { kind: 'sleepLogs', label: 'Sleep', icon: '🌙' },
  { kind: 'walkLogs', label: 'Activity', icon: '🚶' },
  { kind: 'labReports', label: 'Lab report', icon: '🧪' },
  { kind: 'checklistLogs', label: 'Daily ritual', icon: '✅' },
  { kind: 'dailyCheckins', label: 'Check-in', icon: '📋' },
]

export interface LogEntry {
  id: string
  kind: LogKind
  at: unknown
  data: Record<string, unknown>
}

/** One-line human summary for a log entry, tuned per collection's known fields. */
export function summarizeLog(entry: LogEntry): string {
  const d = entry.data
  switch (entry.kind) {
    case 'glucoseReadings': {
      const value = d.value != null ? `${d.value} ${d.unit ?? 'mg/dL'}` : '—'
      const type = typeof d.readingType === 'string' ? d.readingType.replace('_', ' ') : ''
      const status = d.status === 'critical' ? ' · critical' : d.status === 'needs_attention' ? ' · needs attention' : ''
      return `${value}${type ? ` (${type})` : ''}${status}`
    }
    case 'bpReadings': {
      const bp = d.systolic != null && d.diastolic != null ? `${d.systolic}/${d.diastolic} mmHg` : '—'
      return `${bp}${d.status === 'critical' ? ' · critical' : ''}`
    }
    case 'weightLogs':
      return d.weightKg != null ? `${d.weightKg} kg` : d.valueKg != null ? `${d.valueKg} kg` : '—'
    case 'sleepLogs':
      return d.duration != null ? `${d.duration} h` : '—'
    case 'walkLogs': {
      const minutes = d.minutes != null ? `${d.minutes} min` : '—'
      const type = typeof d.activityType === 'string' ? ` · ${d.activityType}` : ''
      return `${minutes}${type}`
    }
    case 'labReports':
      return typeof d.title === 'string' ? d.title : 'Lab report uploaded'
    case 'checklistLogs':
      return typeof d.item === 'string' ? d.item : typeof d.title === 'string' ? d.title : 'Ritual logged'
    case 'dailyCheckins':
      return 'Completed daily check-in'
    default:
      return '—'
  }
}

/** True if this entry looks like it needs a coach's eyes (critical vitals). */
export function isAlertLog(entry: LogEntry): boolean {
  return entry.data.status === 'critical'
}
