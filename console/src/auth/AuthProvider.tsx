import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  onAuthStateChanged,
  signOut as fbSignOut,
  type User,
} from 'firebase/auth'
import { auth } from '../lib/firebase'

export type StaffRole = 'admin' | 'coach' | 'super_admin'
export const STAFF_ROLES: StaffRole[] = ['admin', 'coach', 'super_admin']

export function isStaffRole(role: string | null): role is StaffRole {
  return role != null && (STAFF_ROLES as string[]).includes(role)
}

interface AuthState {
  user: User | null
  /** Custom-claim role from the ID token; null if absent or signed out. */
  role: string | null
  loading: boolean
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [role, setRole] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (nextUser) => {
      setUser(nextUser)
      if (nextUser) {
        try {
          // force refresh so a freshly granted claim is picked up
          const token = await nextUser.getIdTokenResult(true)
          const claimRole = token.claims.role
          setRole(typeof claimRole === 'string' ? claimRole : null)
        } catch {
          setRole(null)
        }
      } else {
        setRole(null)
      }
      setLoading(false)
    })
    return unsub
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      role,
      loading,
      signOut: () => fbSignOut(auth),
    }),
    [user, role, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
