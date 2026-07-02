import { useEffect, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import {
  collection,
  onSnapshot,
  query,
  where,
  type Query,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
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

export default function Dashboard() {
  const now = new Date()
  const weekEnd = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)

  const reports = useCount(() => ({
    primary: query(collection(db, 'reportedMessages'), where('status', '==', 'open')),
    fallback: query(collection(db, 'reportedMessages')),
  }))
  const members = useCount(() => ({
    primary: query(collection(db, 'users')),
    fallback: query(collection(db, 'users')),
  }))
  const programs = useCount(() => ({
    primary: query(collection(db, 'programs')),
    fallback: query(collection(db, 'programs')),
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

  const tiles: TileDef[] = [
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
      state: programs,
    },
    {
      key: 'members',
      label: 'Members',
      hint: 'People across the platform',
      to: '/users',
      accent: 'var(--gold)',
      state: members,
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

      <div className="tile-grid">
        {tiles.map((def) => (
          <Tile key={def.key} def={def} />
        ))}
      </div>
    </section>
  )
}
