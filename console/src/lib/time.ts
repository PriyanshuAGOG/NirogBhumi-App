import type { Timestamp } from 'firebase/firestore'

/** Best-effort conversion of a Firestore timestamp-ish value to a Date. */
export function toDate(value: unknown): Date | null {
  if (!value) return null
  if (value instanceof Date) return value
  if (typeof value === 'object' && typeof (value as Timestamp).toDate === 'function') {
    return (value as Timestamp).toDate()
  }
  if (typeof value === 'object' && typeof (value as { seconds: number }).seconds === 'number') {
    return new Date((value as { seconds: number }).seconds * 1000)
  }
  return null
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 60 * 60 * 24 * 365],
  ['month', 60 * 60 * 24 * 30],
  ['week', 60 * 60 * 24 * 7],
  ['day', 60 * 60 * 24],
  ['hour', 60 * 60],
  ['minute', 60],
  ['second', 1],
]

const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

/** Warm, human relative time e.g. "3 hours ago". */
export function relativeTime(value: unknown): string {
  const date = toDate(value)
  if (!date) return 'just now'
  const diffSeconds = Math.round((date.getTime() - Date.now()) / 1000)
  const abs = Math.abs(diffSeconds)
  if (abs < 30) return 'just now'
  for (const [unit, secondsInUnit] of UNITS) {
    if (abs >= secondsInUnit || unit === 'second') {
      const valueInUnit = Math.round(diffSeconds / secondsInUnit)
      return rtf.format(valueInUnit, unit)
    }
  }
  return 'just now'
}
