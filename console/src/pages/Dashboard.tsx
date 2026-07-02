import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import {
  collection,
  onSnapshot,
  query,
  where,
  Timestamp,
  type Query,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms } from '../lib/usePrograms'
import { consistencyTag } from '../lib/health'
import './Dashboard.css'

interface TileState {
  count: number | null
  error: boolean
}

/** Live count for a query; falls back to counting the whole collection. */
function useCount(build: () => { primary: Query; fallback: Query }): TileState {
  const [state, setState] = useState<TileState>({ count: null, error: false })

  useEffect(() => {
    const { primary, fallback } = build()
    let unsubFallback: (() => void) | null = null

    const startFallback = () => {
      if (unsubFallback) return
      unsubFallback = onSnapshot(
        fallback,
        (snap) => setState({ count: snap.size, error: false }),
        () => setState({ count: null, error: true }),
      )
    }

    const unsubPrimary = onSnapshot(
      primary,
      (snap) => setState({ count: snap.size, error: false }),
      () => startFallback(),
    )

    return () => {
      unsubPrimary()
      if (unsubFallback) unsubFallback()
    }
    // build is stable per-call; deps intentionally empty (one-time wiring).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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

/** Members whose last check-in is stale enough to need a coach's attention. */
function useQuietMembers(): { list: RosterEntry[]; loading: boolean } {
  const [members, setMembers] = useState<RosterEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const unsub = onSnapshot(query(collection(db, 'programMembers')), (snap) => {
      setMembers(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<RosterEntry, 'id'>) })))
      setLoading(false)
    })
    return unsub
  }, [])

  const list = useMemo(
    () => members.filter((m) => consistencyTag(m.lastCheckinAt).cls === 'tag-bad').slice(0, 6),
    [members],
  )

  return { list, loading }
}

export default function Dashboard() {
  const { role } = useAuth()
  const isAdmin = role === 'admin' || role === 'super_admin'
  const { programs } = usePrograms()
  const now = new Date()
  const weekEnd = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)

  const reports = useCount(() => ({
    primary: query(collection(db, 'reportedMessages'), where('status', '==', 'open')),
    fallback: query(collection(db, 'reportedMessages')),
  }))
  const members = useCount(() => ({
    primary: query(collection(db, 'users')),
    fallback: query(collection(db, 'users')),
  }))
  const careMembers = useCount(() => ({
    primary: query(collection(db, 'users'), where('programActive', '==', true)),
    fallback: query(collection(db, 'programMembers')),
  }))
  const newMembers = useCount(() => ({
    primary: query(collection(db, 'users'), where('createdAt', '>=', Timestamp.fromDate(weekAgo))),
    fallback: query(collection(db, 'users')),
  }))
  const events = useCount(() => ({
    primary: query(
      collection(db, 'programEvents'),
      where('startsAt', '>=', now),
      where('startsAt', '<=', weekEnd),
    ),
    fallback: query(collection(db, 'programEvents')),
  }))
  const support = useCount(() => ({
    primary: query(collection(db, 'supportRequests'), where('status', '==', 'open')),
    fallback: query(collection(db, 'supportRequests')),
  }))
  const team = useCount(() => ({
    primary: query(collection(db, 'users'), where('role', 'in', ['admin', 'coach', 'super_admin'])),
    fallback: query(collection(db, 'users')),
  }))

  const { list: quiet, loading: quietLoading } = useQuietMembers()
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
