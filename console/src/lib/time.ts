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

const dateFmt = new Intl.DateTimeFormat('en', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})
const dateTimeFmt = new Intl.DateTimeFormat('en', {
  day: 'numeric',
  month: 'short',
  hour: 'numeric',
  minute: '2-digit',
})
const timeFmt = new Intl.DateTimeFormat('en', {
  hour: 'numeric',
  minute: '2-digit',
})
const monthFmt = new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' })

/** e.g. "Wed, 8 Jul 2026" — null-safe, returns '—' on empty. */
export function formatDate(value: unknown): string {
  const date = toDate(value)
  return date ? dateFmt.format(date) : '—'
}

/** e.g. "8 Jul, 7:00 PM" — null-safe. */
export function formatDateTime(value: unknown): string {
  const date = toDate(value)
  return date ? dateTimeFmt.format(date) : '—'
}

/** e.g. "7:00 PM" — null-safe. */
export function formatTime(value: unknown): string {
  const date = toDate(value)
  return date ? timeFmt.format(date) : '—'
}

/** e.g. "July 2026". */
export function formatMonth(value: Date): string {
  return monthFmt.format(value)
}

/**
 * Convert a timestamp-ish value to the `YYYY-MM-DDTHH:mm` string that a
 * <input type="datetime-local"> expects, in the viewer's local time.
 */
export function toInputDateTime(value: unknown): string {
  const date = toDate(value)
  if (!date) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/** Parse a datetime-local input string into a Date (local time), or null. */
export function fromInputDateTime(value: string): Date | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** Local YYYY-MM-DD key, used for batchStats doc ids (programId_YYYY-MM-DD). */
export function dayKey(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
