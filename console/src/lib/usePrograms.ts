import { useEffect, useState } from 'react'
import { collection, onSnapshot, query, where } from 'firebase/firestore'
import { db } from './firebase'
import { errText } from './errors'
import { useAuth } from '../auth/AuthProvider'

export interface Program {
  id: string
  name?: string
  code?: string
  coachId?: string
  coachName?: string
  coachPhoto?: string
  startDate?: unknown
  durationWeeks?: number
  memberCount?: number
  phases?: { title?: string; startWeek?: number; endWeek?: number }[]
}

/** Firestore `in` filters take at most 30 values; chunk anything longer. */
export function chunk<T>(items: T[], size = 30): T[][] {
  const out: T[][] = []
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size))
  return out
}

/**
 * Live list of `programs`. Admins see every program; a coach sees only the
 * programs they are the assigned coach of. The coach query MUST filter by
 * coachId on the server: Firestore rejects a list query outright unless the
 * rules can prove every possible result is readable, so an unfiltered
 * `programs` query fails for coaches even if their own program exists.
 * Shared by every page that needs a program selector (Batches, Announcements,
 * Calendar, Programs).
 */
export function usePrograms(): {
  programs: Program[]
  loading: boolean
  error: string | null
} {
  const [programs, setPrograms] = useState<Program[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { user, role } = useAuth()
  const isAdmin = role === 'admin' || role === 'super_admin'
  const uid = user?.uid ?? null

  useEffect(() => {
    if (!isAdmin && !uid) return
    const unsub = onSnapshot(
      isAdmin ? query(collection(db, 'programs')) : query(collection(db, 'programs'), where('coachId', '==', uid)),
      (snap) => {
        const next = snap.docs.map((d) => ({
          id: d.id,
          ...(d.data() as Omit<Program, 'id'>),
        }))
        // Sort client-side so we never depend on a field being present.
        next.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
        setPrograms(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load programs'))
      },
    )
    return unsub
  }, [isAdmin, uid])

  return { programs, loading, error }
}
