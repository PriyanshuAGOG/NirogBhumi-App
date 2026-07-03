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

/** Feature areas a coach's access can be scoped to - mirrors PERMISSION_KEYS in functions/src/index.ts. */
export type Permission =
  | 'moderation'
  | 'batches'
  | 'announcements'
  | 'calendar'
  | 'programs'
  | 'consultations'
  | 'support'
  | 'members'
export const PERMISSION_KEYS: Permission[] = [
  'moderation',
  'batches',
  'announcements',
  'calendar',
  'programs',
  'consultations',
  'support',
  'members',
]

interface AuthState {
  user: User | null
  /** Custom-claim role from the ID token; null if absent or signed out. */
  role: string | null
  /** Coach permission scope from the ID token; null for admin/super_admin (full access) or absent. */
  permissions: Permission[] | null
  /** True if `perm` is usable: always true for admin/super_admin, else checks the scope list. */
  hasPermission: (perm: Permission) => boolean
  loading: boolean
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [role, setRole] = useState<string | null>(null)
  const [permissions, setPermissions] = useState<Permission[] | null>(null)
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
          const claimPerms = token.claims.perms
          setPermissions(
            Array.isArray(claimPerms)
              ? claimPerms.filter((p): p is Permission => PERMISSION_KEYS.includes(p as Permission))
              : null,
          )
        } catch {
          setRole(null)
          setPermissions(null)
        }
      } else {
        setRole(null)
        setPermissions(null)
      }
      setLoading(false)
    })
    return unsub
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      role,
      permissions,
      // A coach with no `perms` claim yet (pre-dates this feature, or an admin
      // hasn't scoped them down) defaults to full access rather than none -
      // narrowing only takes effect once an admin explicitly sets the list.
      hasPermission: (perm) =>
        role === 'admin' || role === 'super_admin' || permissions === null || permissions.includes(perm),
      loading,
      signOut: () => fbSignOut(auth),
    }),
    [user, role, permissions, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
