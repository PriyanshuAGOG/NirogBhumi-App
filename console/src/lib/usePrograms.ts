import { useEffect, useState } from 'react'
import { collection, onSnapshot, query } from 'firebase/firestore'
import { db } from './firebase'
import { errText } from './errors'

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

/**
 * Live list of `programs`, newest cohorts first. Shared by every page that
 * needs a program selector (Batches, Announcements, Calendar, Programs).
 */
export function usePrograms(): {
  programs: Program[]
  loading: boolean
  error: string | null
} {
  const [programs, setPrograms] = useState<Program[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'programs')),
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
  }, [])

  return { programs, loading, error }
}
