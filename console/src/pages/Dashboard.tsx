import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import {
  collection,
  getCountFromServer,
  limit,
  onSnapshot,
  orderBy,
  query,
  where,
  Timestamp,
  type Query,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { chunk } from '../lib/usePrograms'
import { useScope } from '../lib/scope'
import './Dashboard.css'

interface TileState {
  count: number | null
  error: boolean
}

// Refresh cadence for dashboard tile counts. These are stat tiles, not
// lists a coach reads line-by-line - they don't need sub-second realtime,
// and the old approach (a live onSnapshot listener on the *entire* `users`
// collection just to read snap.size) re-fired the whole Dashboard on every
// single check-in from every member in the app, since users/{uid} gets
// touched on every one (checkinStreak, checkinHourHint, lastCheckinAt...).
// A periodic count() aggregation query - which Firestore bills and executes
// as a single number, never downloading the matched documents - gets the
// same "feels live" tile without that cost.
const COUNT_REFRESH_MS = 45_000

/**
 * Periodically refreshed aggregate count. `build` returns the queries to count
 * (summed together - a coach's program-scoped counts are several chunked
 * queries), or null when there is nothing to count yet (e.g. a coach with no
 * program), which shows 0. Falls back to `fallback` if the primary fails.
 */
function useCount(build: () => { primary: Query[]; fallback?: Query[] } | null, deps: unknown[] = []): TileState {
  const [state, setState] = useState<TileState>({ count: null, error: false })

  useEffect(() => {
    const queries = build()
    let cancelled = false
    if (!queries) {
      setState({ count: 0, error: false })
      return
    }
    const total = async (list: Query[]) => {
      const snaps = await Promise.all(list.map((q) => getCountFromServer(q)))
      return snaps.reduce((sum, snap) => sum + snap.data().count, 0)
    }

    async function refresh() {
      try {
        const count = await total(queries!.primary)
        if (!cancelled) setState({ count, error: false })
      } catch {
        try {
          if (!queries!.fallback) throw new Error('no fallback')
          const count = await total(queries!.fallback)
          if (!cancelled) setState({ count, error: false })
        } catch {
          if (!cancelled) setState({ count: null, error: true })
        }
      }
    }

    void refresh()
    const interval = setInterval(() => void refresh(), COUNT_REFRESH_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return state
}

interface TileDef {
  key: string
  label: string
  hint: string
  to: string
  accent: string
  state: TileState
}

function Tile({ def }: { def: TileDef }) {
  const { count, error } = def.state
  return (
    <Link
      to={def.to}
      className="card tile"
      style={{ '--tile-accent': def.accent } as CSSProperties}
    >
      <span className="tile-label">{def.label}</span>
      <span className="tile-count">
        {error ? '—' : count === null ? <span className="tile-dot" aria-hidden /> : count}
      </span>
      <span className="tile-hint">{def.hint}</span>
    </Link>
  )
}

interface RosterEntry {
  id: string
  uid?: string
  name?: string
  programId?: string
  lastCheckinAt?: unknown
}

const QUIET_AFTER_MS = 4 * 24 * 60 * 60 * 1000

/**
 * Members whose last check-in is stale enough to need a coach's attention.
 * Filtered server-side (lastCheckinAt <= 4 days ago) instead of live-listening
 * every programMembers doc on the platform - that field is mirrored onto
 * every roster doc on each check-in (see the onUserCheckinMirror function),
 * so without this bound the widget would re-fire for every admin on every
 * single check-in from every member, not just the ones actually going quiet.
 */
function useQuietMembers(scope: { isAdmin: boolean; programIds: string[]; programKey: string; loading: boolean }): { list: RosterEntry[]; loading: boolean } {
  const [list, setList] = useState<RosterEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!scope.isAdmin && scope.loading) return
    const cutoffMs = Date.now() - QUIET_AFTER_MS

    if (scope.isAdmin) {
      const unsub = onSnapshot(
        query(collection(db, 'programMembers'), where('lastCheckinAt', '<=', Timestamp.fromMillis(cutoffMs)), orderBy('lastCheckinAt', 'asc'), limit(6)),
        (snap) => {
          setList(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<RosterEntry, 'id'>) })))
          setLoading(false)
        },
        () => setLoading(false),
      )
      return unsub
    }

    // A coach can only list rosters of their own programs, so read those and
    // pick out the quiet members here (a batch roster is small).
    if (!scope.programIds.length) {
      setList([])
      setLoading(false)
      return
    }
    const perChunk = new Map<number, RosterEntry[]>()
    const publish = () => {
      const all = Array.from(perChunk.values()).flat()
      const quiet = all
        .filter((m) => {
          const last = (m.lastCheckinAt as { toMillis?: () => number } | undefined)?.toMillis?.()
          return last != null && last <= cutoffMs
        })
        .sort((a, b) => ((a.lastCheckinAt as { toMillis: () => number }).toMillis()) - ((b.lastCheckinAt as { toMillis: () => number }).toMillis()))
        .slice(0, 6)
      setList(quiet)
      setLoading(false)
    }
    const unsubs = chunk(scope.programIds).map((ids, index) =>
      onSnapshot(
        query(collection(db, 'programMembers'), where('programId', 'in', ids)),
        (snap) => {
          perChunk.set(index, snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<RosterEntry, 'id'>) })))
          publish()
        },
        () => setLoading(false),
      ),
    )
    return () => unsubs.forEach((u) => u())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope.isAdmin, scope.programKey, scope.loading])

  return { list, loading }
}

export default function Dashboard() {
  const scope = useScope()
  const { isAdmin, programs, programIds, programKey } = scope
  const now = new Date()
  const weekEnd = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)

  // Coaches can only count data tied to programs they coach (see lib/scope.ts);
  // an admin counts platform-wide. `scoped(...)` is null for a coach who has
  // no program yet, which the tiles show as 0.
  const scoped = (build: (ids: string[]) => Query) =>
    isAdmin ? null : programIds.length ? chunk(programIds).map(build) : undefined
  const scopeDeps = [isAdmin, programKey, scope.loading]
  const waiting = !isAdmin && scope.loading

  const reports = useCount(() => {
    if (waiting) return { primary: [] }
    if (isAdmin) return { primary: [query(collection(db, 'reportedMessages'), where('status', '==', 'open'))], fallback: [query(collection(db, 'reportedMessages'))] }
    const list = scoped((ids) => query(collection(db, 'reportedMessages'), where('programId', 'in', ids), where('status', '==', 'open')))
    return list ? { primary: list } : null
  }, scopeDeps)
  const members = useCount(() => (isAdmin ? { primary: [query(collection(db, 'users'))] } : null), scopeDeps)
  const careMembers = useCount(() => {
    if (waiting) return { primary: [] }
    if (isAdmin) return { primary: [query(collection(db, 'users'), where('programActive', '==', true))], fallback: [query(collection(db, 'programMembers'))] }
    const list = scoped((ids) => query(collection(db, 'programMembers'), where('programId', 'in', ids)))
    return list ? { primary: list } : null
  }, scopeDeps)
  const newMembers = useCount(() => (isAdmin ? { primary: [query(collection(db, 'users'), where('createdAt', '>=', Timestamp.fromDate(weekAgo)))] } : null), scopeDeps)
  const events = useCount(() => {
    if (waiting) return { primary: [] }
    if (isAdmin) return { primary: [query(collection(db, 'programEvents'), where('startsAt', '>=', now), where('startsAt', '<=', weekEnd))], fallback: [query(collection(db, 'programEvents'))] }
    const list = scoped((ids) => query(collection(db, 'programEvents'), where('programId', 'in', ids), where('startsAt', '>=', now), where('startsAt', '<=', weekEnd)))
    return list ? { primary: list } : null
  }, scopeDeps)
  const support = useCount(() => ({ primary: [query(collection(db, 'supportRequests'), where('status', '==', 'open'))], fallback: [query(collection(db, 'supportRequests'))] }))
  const team = useCount(() => (isAdmin ? { primary: [query(collection(db, 'users'), where('role', 'in', ['admin', 'coach', 'super_admin']))] } : null), scopeDeps)

  const { list: quiet, loading: quietLoading } = useQuietMembers(scope)
  const programName = useMemo(() => {
    const map = new Map(programs.map((p) => [p.id, p.name ?? 'Program']))
    return (id?: string) => (id ? map.get(id) ?? 'Program' : '—')
  }, [programs])

  const alertTiles: TileDef[] = [
    {
      key: 'reports',
      label: 'Open reports',
      hint: 'Flagged chat messages awaiting review',
      to: '/moderation',
      accent: 'var(--status-critical)',
      state: reports,
    },
    {
      key: 'support',
      label: 'Support requests',
      hint: 'Unresolved member questions',
      to: '/support',
      accent: 'var(--status-attention)',
      state: support,
    },
    {
      key: 'quiet',
      label: 'Needs attention',
      hint: 'Care+ members quiet for 4+ days',
      to: '/members',
      accent: 'var(--terracotta)',
      state: { count: quietLoading ? null : quiet.length, error: false },
    },
  ]

  const careTiles: TileDef[] = [
    {
      key: 'care-members',
      label: 'Active Care+ members',
      hint: 'Enrolled in a program right now',
      to: '/members',
      accent: 'var(--status-in-range)',
      state: careMembers,
    },
    {
      key: 'events',
      label: 'Events this week',
      hint: 'Sessions, walks and labs in the next 7 days',
      to: '/calendar',
      accent: 'var(--forest-soft)',
      state: events,
    },
    {
      key: 'programs',
      label: 'Programs',
      hint: 'Active batches you run',
      to: '/programs',
      accent: 'var(--status-in-range)',
      state: { count: programs.length, error: false },
    },
  ]

  const growthTiles: TileDef[] = [
    {
      key: 'members',
      label: 'Total members',
      hint: 'People across the platform',
      to: '/users',
      accent: 'var(--gold)',
      state: members,
    },
    {
      key: 'new-members',
      label: 'New this week',
      hint: 'Signed up in the last 7 days',
      to: '/users',
      accent: 'var(--gold)',
      state: newMembers,
    },
    {
      key: 'team',
      label: 'Team & access',
      hint: 'Admins and coaches with console access',
      to: '/users',
      accent: 'var(--ink-secondary)',
      state: team,
    },
  ]

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Overview</span>
        <h1>Console home</h1>
        <p className="page-lede">
          A calm, live snapshot of the community. Everything below updates in real
          time — tap a tile to go straight to the work.
        </p>
      </header>

      <div className="dash-section">
        <h2 className="dash-section-title">Needs attention</h2>
        <div className="tile-grid">
          {alertTiles.map((def) => (
            <Tile key={def.key} def={def} />
          ))}
        </div>
      </div>

      {quiet.length > 0 && (
        <div className="card quiet-card">
          <span className="overline">Quiet Care+ members</span>
          <p className="page-lede quiet-lede">
            No check-in in 4+ days. A quick message or call now can keep their rhythm going.
          </p>
          <div className="quiet-list">
            {quiet.map((m) => (
              <Link key={m.id} to={`/members/${m.uid ?? m.id}`} className="quiet-row">
                <span className="quiet-name">{m.name ?? m.uid ?? 'Member'}</span>
                <span className="quiet-program">{programName(m.programId)}</span>
              </Link>
            ))}
          </div>
        </div>
      )}

      <div className="dash-section">
        <h2 className="dash-section-title">Care+</h2>
        <div className="tile-grid">
          {careTiles.map((def) => (
            <Tile key={def.key} def={def} />
          ))}
        </div>
      </div>

      {isAdmin && (
        <div className="dash-section">
          <h2 className="dash-section-title">Platform</h2>
          <div className="tile-grid">
            {growthTiles.map((def) => (
              <Tile key={def.key} def={def} />
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
