import { useEffect, useMemo, useState } from 'react'
import { collection, limit, onSnapshot, orderBy, query } from 'firebase/firestore'
import { db } from './firebase'
import { errText } from './errors'
import { toDate } from './time'

/** One problem the app reported. Signed-in failures arrive one per event; sign-in problems and crashes arrive grouped, with a count. */
export interface ErrorReport {
  id: string
  userId?: string | null
  screen?: string
  message?: string
  code?: string | null
  detail?: string
  resolved?: boolean
  createdAt?: unknown
  firstSeenAt?: unknown
  reviewedBy?: string
  reviewedAt?: unknown
  source?: string
  count?: number
  installs?: string[]
  appVersion?: string
  versionCode?: number | null
  device?: string
  androidSdk?: number | null
  signedIn?: boolean
}

export type ErrorKind = 'sign_in' | 'crash' | 'app'

export function kindOf(item: Pick<ErrorReport, 'screen'>): ErrorKind {
  const screen = item.screen ?? ''
  if (screen.startsWith('sign_in:')) return 'sign_in'
  if (screen === 'crash') return 'crash'
  return 'app'
}

const SIGN_IN_TITLES: Record<string, string> = {
  phone_send: 'SMS code could not be sent',
  phone_no_code: 'SMS code never arrived (member chose email)',
  phone_code_check: 'SMS code was rejected',
  phone_auto_verify: 'Automatic phone check failed',
  email_sign_in: 'Email sign-in failed',
  email_sign_up: 'Email sign-up failed',
  google: 'Google sign-in failed',
  link_email: 'Adding an email to the account failed',
  link_phone: 'Adding a phone number to the account failed',
  link_phone_auto: 'Adding a phone number failed (automatic check)',
  link_phone_no_code: 'SMS code never arrived when adding a number',
}

/** A plain-English title for the technical screen/operation name the app sent. */
export function describeScreen(screen: string | undefined): string {
  if (!screen) return 'Unknown operation'
  if (screen === 'crash') return 'App crashed'
  if (screen.startsWith('sign_in:')) return `Sign-in: ${SIGN_IN_TITLES[screen.slice(8)] ?? screen.slice(8).replace(/_/g, ' ')}`
  return screen
}

export function lastSeenMillis(item: Pick<ErrorReport, 'createdAt'>): number {
  return toDate(item.createdAt)?.getTime() ?? 0
}

export interface ErrorSummary {
  reports: ErrorReport[]
  open: ErrorReport[]
  openSignIn: number
  openCrashes: number
  /** Occurrences, not rows: a problem seen 40 times counts 40. */
  openOccurrences: number
  latest: ErrorReport | null
  loading: boolean
  error: string | null
}

/** Live view of the newest reports. Admins only (the rules refuse everyone else), so pass enabled=false for coaches. */
export function useErrorReports(enabled: boolean): ErrorSummary {
  const [reports, setReports] = useState<ErrorReport[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!enabled) {
      setReports([])
      setLoading(false)
      return
    }
    const unsub = onSnapshot(
      query(collection(db, 'errorReports'), orderBy('createdAt', 'desc'), limit(300)),
      (snap) => {
        setReports(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ErrorReport, 'id'>) })))
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load error reports'))
      },
    )
    return unsub
  }, [enabled])

  return useMemo(() => {
    const open = reports.filter((r) => r.resolved !== true)
    return {
      reports,
      open,
      openSignIn: open.filter((r) => kindOf(r) === 'sign_in').length,
      openCrashes: open.filter((r) => kindOf(r) === 'crash').length,
      openOccurrences: open.reduce((sum, r) => sum + Math.max(1, r.count ?? 1), 0),
      latest: open[0] ?? null,
      loading,
      error,
    }
  }, [reports, loading, error])
}
