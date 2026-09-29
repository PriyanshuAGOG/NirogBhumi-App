import { useMemo } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms, type Program } from './usePrograms'

export interface Scope {
  /** admin / super_admin: everything on the platform. */
  isAdmin: boolean
  uid: string | null
  /** The programs this signed-in person may see (all of them for an admin). */
  programs: Program[]
  programIds: string[]
  /** Stable string form of programIds, safe to use as an effect dependency. */
  programKey: string
  loading: boolean
  error: string | null
}

/**
 * Who am I looking at data for? Coaches can only read data tied to the
 * programs they coach, and Firestore rejects any list query that isn't
 * provably limited to that - so pages use `programIds` to build
 * `where('programId', 'in', ...)` queries for coaches instead of listing a
 * whole collection.
 */
export function useScope(): Scope {
  const { user, role } = useAuth()
  const { programs, loading, error } = usePrograms()
  const isAdmin = role === 'admin' || role === 'super_admin'
  return useMemo(() => {
    const programIds = programs.map((p) => p.id)
    return { isAdmin, uid: user?.uid ?? null, programs, programIds, programKey: programIds.join(','), loading, error }
  }, [isAdmin, user?.uid, programs, loading, error])
}
