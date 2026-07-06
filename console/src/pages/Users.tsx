import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, limit, onSnapshot, orderBy, query } from 'firebase/firestore'
import { httpsCallable, type HttpsCallableResult } from 'firebase/functions'
import { FirebaseError } from 'firebase/app'
import { db, functions } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { errText } from '../lib/errors'
import { PERMISSION_KEYS, type Permission } from '../auth/AuthProvider'
import './Users.css'

interface UserRow {
  id: string
  userId?: string
  name?: string
  email?: string
  phone?: string
  role?: string
  status?: string
  programActive?: boolean
  permissions?: Permission[]
}

type AssignableRole = 'user' | 'coach' | 'admin'
const ROLE_OPTIONS: AssignableRole[] = ['user', 'coach', 'admin']

const PERMISSION_LABELS: Record<Permission, string> = {
  moderation: 'Moderation',
  batches: 'Batches',
  announcements: 'Announcements',
  calendar: 'Calendar',
  programs: 'Programs',
  consultations: 'Consultations',
  support: 'Support',
  members: 'Member monitoring',
}

// A callable's "not deployed" surfaces as one of these codes.
const NOT_DEPLOYED = new Set(['functions/not-found', 'functions/unavailable', 'functions/internal'])

interface RowState {
  saving: boolean
  message: string | null
  error: string | null
}

function roleTagClass(role: string | undefined): string {
  switch (role) {
    case 'admin':
    case 'super_admin':
      return 'tag-good'
    case 'coach':
      return 'tag-warn'
    default:
      return 'tag-neutral'
  }
}

export default function Users() {
  const { role: viewerRole } = useAuth()
  const isSuperAdmin = viewerRole === 'super_admin'
  // Only a super admin may grant the admin role or change an existing
  // admin's role - the setUserRole function enforces this server-side too;
  // this just keeps a plain admin from attempting (and being rejected on) a
  // change that can never succeed.
  const roleOptions = isSuperAdmin ? ROLE_OPTIONS : (['user', 'coach'] as AssignableRole[])
  const [users, setUsers] = useState<UserRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [pending, setPending] = useState<Record<string, AssignableRole>>({})
  const [pendingPerms, setPendingPerms] = useState<Record<string, Permission[]>>({})
  const [rowState, setRowState] = useState<Record<string, RowState>>({})

  useEffect(() => {
    // Bounded + ordered - an unfiltered listener on the whole `users`
    // collection re-fires for every admin viewing this page on every
    // check-in anywhere in the app (users/{uid} is touched on each one).
    // Newest-first with a cap keeps this page reactive without paying for
    // the entire platform's write volume.
    const unsub = onSnapshot(
      query(collection(db, 'users'), orderBy('createdAt', 'desc'), limit(500)),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<UserRow, 'id'>) }))
        next.sort((a, b) => (a.name ?? a.email ?? '').localeCompare(b.name ?? b.email ?? ''))
        setUsers(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load users'))
      },
    )
    return unsub
  }, [])

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return users
    return users.filter((u) =>
      [u.name, u.email, u.phone, u.id].some((v) => v?.toLowerCase().includes(q)),
    )
  }, [users, search])

  async function apply(u: UserRow) {
    const nextRole = pending[u.id] ?? (u.role as AssignableRole) ?? 'user'
    const nextPerms = nextRole === 'coach' ? pendingPerms[u.id] ?? u.permissions ?? PERMISSION_KEYS : undefined
    setRowState((prev) => ({ ...prev, [u.id]: { saving: true, message: null, error: null } }))
    try {
      const setUserRole = httpsCallable<{ uid: string; role: string; permissions?: string[] }, unknown>(
        functions,
        'setUserRole',
      )
      const res: HttpsCallableResult<unknown> = await setUserRole({
        uid: u.userId ?? u.id,
        role: nextRole,
        ...(nextPerms ? { permissions: nextPerms } : {}),
      })
      void res
      setRowState((prev) => ({
        ...prev,
        [u.id]: { saving: false, message: `Role set to ${nextRole}.`, error: null },
      }))
    } catch (err) {
      const notDeployed = err instanceof FirebaseError && NOT_DEPLOYED.has(err.code)
      setRowState((prev) => ({
        ...prev,
        [u.id]: {
          saving: false,
          message: null,
          error: notDeployed
            ? 'Role service not deployed yet.'
            : errText(err, 'Could not set role'),
        },
      }))
    }
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Users &amp; roles</span>
        <h1>Users &amp; roles</h1>
        <p className="page-lede">
          Everyone on the platform. Change a person's role — this calls a secured
          function that updates their access.
        </p>
      </header>

      <div className="toolbar">
        <input
          className="input user-search"
          placeholder="Search by name, email or phone…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading users…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🧑‍🤝‍🧑</div>
          <p className="empty-title">No users found</p>
          <p className="empty-sub">
            {search ? 'Try a different search.' : 'Users will appear here as people sign up.'}
          </p>
        </div>
      ) : (
        <div className="user-list">
          {visible.map((u) => {
            const targetRole = u.role ?? 'user'
            // A plain admin can't touch another admin/super_admin account
            // (setUserRole enforces this server-side) - lock the row instead
            // of letting them attempt a change that can only fail.
            const isTargetElevated = targetRole === 'admin' || targetRole === 'super_admin'
            const canEdit = isSuperAdmin || !isTargetElevated
            const current = targetRole as AssignableRole
            const selected = pending[u.id] ?? current
            // super_admin isn't an assignable option (setUserRole never grants
            // it), but it needs to appear so the select shows the real current
            // role instead of silently falling back to "user".
            const displayOptions = targetRole === 'super_admin' ? ['super_admin' as const, ...roleOptions] : roleOptions
            const state = rowState[u.id]
            const currentPerms = u.permissions ?? PERMISSION_KEYS
            const selectedPerms = pendingPerms[u.id] ?? currentPerms
            const changed =
              selected !== current ||
              (selected === 'coach' &&
                JSON.stringify([...selectedPerms].sort()) !== JSON.stringify([...currentPerms].sort()))
            return (
              <div key={u.id} className="card user-row-card">
                <div className="user-row">
                  <div className="user-main">
                    <span className="user-name">{u.name ?? '(no name)'}</span>
                    <span className="user-sub">
                      {u.email ?? '—'}
                      {u.phone ? ` · ${u.phone}` : ''}
                    </span>
                    {state?.message && <span className="user-ok">{state.message}</span>}
                    {state?.error && <span className="user-err">{state.error}</span>}
                  </div>
                  <div className="user-meta">
                    {u.programActive && (
                      <Link to={`/members/${u.userId ?? u.id}`} className="btn btn-ghost btn-sm">
                        Health record
                      </Link>
                    )}
                    <span className={`tag ${roleTagClass(u.role)}`}>{u.role ?? 'user'}</span>
                    {canEdit ? (
                      <>
                        <select
                          className="select user-role-select"
                          value={selected}
                          onChange={(e) =>
                            setPending((prev) => ({ ...prev, [u.id]: e.target.value as AssignableRole }))
                          }
                        >
                          {displayOptions.map((r) => (
                            <option key={r} value={r} disabled={r === 'super_admin'}>
                              {r}
                            </option>
                          ))}
                        </select>
                        <button
                          className="btn btn-forest btn-sm"
                          disabled={!changed || state?.saving}
                          onClick={() => void apply(u)}
                        >
                          {state?.saving ? 'Saving…' : 'Set role'}
                        </button>
                      </>
                    ) : (
                      <span className="user-sub">Super admin only</span>
                    )}
                  </div>
                </div>
                {canEdit && selected === 'coach' && (
                  <div className="perm-grid">
                    <span className="perm-label">Console access for this coach</span>
                    <div className="perm-checks">
                      {PERMISSION_KEYS.map((p) => (
                        <label key={p} className="check">
                          <input
                            type="checkbox"
                            checked={selectedPerms.includes(p)}
                            onChange={(e) =>
                              setPendingPerms((prev) => {
                                const base = prev[u.id] ?? currentPerms
                                const next = e.target.checked ? [...base, p] : base.filter((x) => x !== p)
                                return { ...prev, [u.id]: next }
                              })
                            }
                          />
                          {PERMISSION_LABELS[p]}
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
