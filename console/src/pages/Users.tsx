import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, limit, onSnapshot, orderBy, query } from 'firebase/firestore'
import { httpsCallable, type HttpsCallableResult } from 'firebase/functions'
import { FirebaseError } from 'firebase/app'
import { db, functions } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { errText } from '../lib/errors'
import { PERMISSION_KEYS, type Permission } from '../auth/AuthProvider'
import { usePrograms } from '../lib/usePrograms'
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
  activeProgramName?: string
  permissions?: Permission[]
}

type AssignableRole = 'user' | 'coach' | 'admin' | 'super_admin'
const ROLE_OPTIONS: AssignableRole[] = ['user', 'coach', 'admin', 'super_admin']

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

type NewStaffRole = 'coach' | 'admin' | 'super_admin'

const BOOTSTRAP_SUPER_ADMIN_EMAIL = 'priyanshu@nirogbhumi.com'

export default function Users() {
  const { user, role: viewerRole, hasPermission } = useAuth()
  const isSuperAdmin = viewerRole === 'super_admin'
  const canManagePrograms = isSuperAdmin || viewerRole === 'admin' || hasPermission('programs')
  const { programs } = usePrograms()
  const [enrollingFor, setEnrollingFor] = useState<string | null>(null)
  const [enrollProgramId, setEnrollProgramId] = useState('')
  const [enrolling, setEnrolling] = useState(false)
  const canBootstrap = !isSuperAdmin && user?.email?.toLowerCase() === BOOTSTRAP_SUPER_ADMIN_EMAIL
  const [bootstrapping, setBootstrapping] = useState(false)
  const [bootstrapError, setBootstrapError] = useState<string | null>(null)
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

  const [showAddStaff, setShowAddStaff] = useState(false)
  const [newEmail, setNewEmail] = useState('')
  const [newName, setNewName] = useState('')
  const [newRole, setNewRole] = useState<NewStaffRole>('coach')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [created, setCreated] = useState<{ email: string; tempPassword: string } | null>(null)

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

  async function enrollUser(u: UserRow) {
    if (!enrollProgramId) return
    setEnrolling(true)
    setRowState((prev) => ({ ...prev, [u.id]: { saving: false, message: null, error: null } }))
    try {
      const adminEnrollUser = httpsCallable<
        { uid: string; programId: string },
        { activeProgramName: string }
      >(functions, 'adminEnrollUser')
      const res = await adminEnrollUser({ uid: u.userId ?? u.id, programId: enrollProgramId })
      setRowState((prev) => ({
        ...prev,
        [u.id]: { saving: false, message: `Enrolled in ${res.data.activeProgramName}.`, error: null },
      }))
      setEnrollingFor(null)
      setEnrollProgramId('')
    } catch (err) {
      const notDeployed = err instanceof FirebaseError && NOT_DEPLOYED.has(err.code)
      setRowState((prev) => ({
        ...prev,
        [u.id]: {
          saving: false,
          message: null,
          error: notDeployed ? 'Enrollment service not deployed yet.' : errText(err, 'Could not enroll'),
        },
      }))
    } finally {
      setEnrolling(false)
    }
  }

  async function claimSuperAdmin() {
    if (!user) return
    setBootstrapping(true)
    setBootstrapError(null)
    try {
      const bootstrapSuperAdmin = httpsCallable(functions, 'bootstrapSuperAdmin')
      await bootstrapSuperAdmin()
      // Only a full reload reliably re-syncs every claim-gated bit of state
      // across the app (nav, permission checks, this very page) after a
      // one-time role change like this - simpler and safer than threading a
      // manual refresh through everywhere that reads `role` from useAuth().
      await user.getIdTokenResult(true)
      window.location.reload()
    } catch (err) {
      const notDeployed = err instanceof FirebaseError && NOT_DEPLOYED.has(err.code)
      setBootstrapError(notDeployed ? 'Bootstrap service not deployed yet.' : errText(err, 'Could not claim super admin'))
      setBootstrapping(false)
    }
  }

  async function createStaff() {
    const email = newEmail.trim()
    if (!email) return
    setCreating(true)
    setCreateError(null)
    try {
      const createStaffAccount = httpsCallable<
        { email: string; role: NewStaffRole; name?: string },
        { uid: string; email: string; role: string; tempPassword: string }
      >(functions, 'createStaffAccount')
      const res = await createStaffAccount({ email, role: newRole, name: newName.trim() || undefined })
      setCreated({ email: res.data.email, tempPassword: res.data.tempPassword })
      setNewEmail('')
      setNewName('')
      setNewRole('coach')
      setShowAddStaff(false)
    } catch (err) {
      const notDeployed = err instanceof FirebaseError && NOT_DEPLOYED.has(err.code)
      setCreateError(notDeployed ? 'Account creation service not deployed yet.' : errText(err, 'Could not create account'))
    } finally {
      setCreating(false)
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

      {canBootstrap && (
        <div className="banner banner-info" role="status">
          <strong>This account is eligible for the one-time super admin bootstrap.</strong>{' '}
          Claiming it lets you promote other accounts to admin or super admin.
          {bootstrapError && <p className="user-err">{bootstrapError}</p>}
          <div className="composer-actions">
            <button className="btn btn-forest" disabled={bootstrapping} onClick={() => void claimSuperAdmin()}>
              {bootstrapping ? 'Claiming…' : 'Claim super admin'}
            </button>
          </div>
        </div>
      )}

      {created && (
        <div className="banner banner-success" role="status">
          <strong>{created.email}</strong> was created. Temporary password (shown once —
          copy it to them now): <code className="user-temp-pass">{created.tempPassword}</code>{' '}
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => {
              void navigator.clipboard.writeText(created.tempPassword)
            }}
          >
            Copy
          </button>{' '}
          <button className="btn btn-ghost btn-sm" onClick={() => setCreated(null)}>
            Dismiss
          </button>
          <p className="user-temp-note">
            They should sign in at this console with this password, then use "Forgot password"
            on the sign-in screen to set their own.
          </p>
        </div>
      )}

      <div className="toolbar">
        <input
          className="input user-search"
          placeholder="Search by name, email or phone…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button className="btn btn-forest" onClick={() => setShowAddStaff((v) => !v)}>
          {showAddStaff ? 'Cancel' : 'Add admin/coach'}
        </button>
      </div>

      {showAddStaff && (
        <div className="card composer">
          <span className="overline">New staff account</span>
          {createError && (
            <div className="banner banner-error" role="alert">
              {createError}
            </div>
          )}
          <div className="field">
            <span className="field-label">Email</span>
            <input
              className="input"
              type="email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              placeholder="name@nirogbhumi.app"
            />
          </div>
          <div className="field">
            <span className="field-label">Name (optional)</span>
            <input className="input" value={newName} onChange={(e) => setNewName(e.target.value)} />
          </div>
          <div className="field">
            <span className="field-label">Role</span>
            <select className="select" value={newRole} onChange={(e) => setNewRole(e.target.value as NewStaffRole)}>
              <option value="coach">Coach</option>
              {isSuperAdmin && <option value="admin">Admin</option>}
              {isSuperAdmin && <option value="super_admin">Super Admin</option>}
            </select>
          </div>
          <div className="composer-actions">
            <button
              className="btn btn-forest"
              disabled={creating || !newEmail.trim()}
              onClick={() => void createStaff()}
            >
              {creating ? 'Creating…' : 'Create account'}
            </button>
          </div>
        </div>
      )}

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
            // A plain admin's roleOptions omits super_admin entirely, but the
            // select still needs to show it as the current value for an
            // already-super_admin row instead of silently falling back to "user".
            const displayOptions = targetRole === 'super_admin' && !roleOptions.includes('super_admin')
              ? ['super_admin' as const, ...roleOptions]
              : roleOptions
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
                    {u.programActive ? (
                      <span className="tag tag-good">{u.activeProgramName ?? 'In program'}</span>
                    ) : (
                      <span className="tag tag-neutral">Not enrolled</span>
                    )}
                    {canManagePrograms && (
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => {
                          if (enrollingFor === u.id) {
                            setEnrollingFor(null)
                          } else {
                            setEnrollingFor(u.id)
                            setEnrollProgramId(programs[0]?.id ?? '')
                          }
                        }}
                      >
                        {u.programActive ? 'Move program' : 'Enroll'}
                      </button>
                    )}
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
                            <option key={r} value={r} disabled={r === 'super_admin' && !isSuperAdmin}>
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
                {enrollingFor === u.id && (
                  <div className="perm-grid">
                    <span className="perm-label">
                      {u.programActive ? 'Move to a different program' : 'Enroll in a program'}
                    </span>
                    <div className="composer-actions" style={{ justifyContent: 'flex-start', gap: 8 }}>
                      <select
                        className="select"
                        value={enrollProgramId}
                        onChange={(e) => setEnrollProgramId(e.target.value)}
                      >
                        {programs.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name ?? p.id}
                          </option>
                        ))}
                      </select>
                      <button
                        className="btn btn-forest btn-sm"
                        disabled={enrolling || !enrollProgramId}
                        onClick={() => void enrollUser(u)}
                      >
                        {enrolling ? 'Enrolling…' : 'Confirm'}
                      </button>
                      <button className="btn btn-ghost btn-sm" onClick={() => setEnrollingFor(null)}>
                        Cancel
                      </button>
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
