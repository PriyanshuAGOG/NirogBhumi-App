import { useEffect, useMemo, useState } from 'react'
import { collection, onSnapshot, query } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { errText } from '../lib/errors'
import { relativeTime, toDate } from '../lib/time'
import './Consultations.css'

interface Consultation {
  id: string
  userId?: string
  consultationType?: string
  status?: string
  paymentStatus?: string
  slotId?: string
  createdAt?: unknown
}

function shortId(id: string | undefined): string {
  if (!id) return 'unknown'
  return id.length > 8 ? `${id.slice(0, 6)}…` : id
}

function statusTag(status: string | undefined): string {
  switch (status) {
    case 'completed':
    case 'confirmed':
      return 'tag-good'
    case 'cancelled':
    case 'no_show':
      return 'tag-bad'
    case 'pending':
      return 'tag-warn'
    default:
      return 'tag-neutral'
  }
}

export default function Consultations() {
  const [items, setItems] = useState<Consultation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<string>('all')

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'consultations')),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Consultation, 'id'>) }))
        next.sort(
          (a, b) => (toDate(b.createdAt)?.getTime() ?? 0) - (toDate(a.createdAt)?.getTime() ?? 0),
        )
        setItems(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load consultations'))
      },
    )
    return unsub
  }, [])

  const statuses = useMemo(() => {
    const set = new Set<string>()
    items.forEach((i) => i.status && set.add(i.status))
    return Array.from(set).sort()
  }, [items])

  const visible = items.filter((i) => filter === 'all' || i.status === filter)

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Consultations</span>
        <h1>Consultations</h1>
        <p className="page-lede">
          A read-only view of consultation bookings. Payments are handled elsewhere —
          this is for visibility only.
        </p>
      </header>

      <div className="toolbar">
        <div className="seg" role="tablist">
          <button
            className={'seg-btn' + (filter === 'all' ? ' seg-btn-active' : '')}
            onClick={() => setFilter('all')}
          >
            All
          </button>
          {statuses.map((s) => (
            <button
              key={s}
              className={'seg-btn' + (filter === s ? ' seg-btn-active' : '')}
              onClick={() => setFilter(s)}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading consultations…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🩺</div>
          <p className="empty-title">No consultations</p>
          <p className="empty-sub">
            {filter === 'all' ? 'Bookings will appear here as members request them.' : `No ${filter} consultations.`}
          </p>
        </div>
      ) : (
        <div className="cons-list">
          {visible.map((c) => (
            <div key={c.id} className="card cons-row">
              <div className="cons-main">
                <span className="cons-type">{c.consultationType ?? 'Consultation'}</span>
                <span className="cons-sub">
                  Member {shortId(c.userId)}
                  {c.slotId ? ` · slot ${shortId(c.slotId)}` : ''}
                  {' · '}
                  {relativeTime(c.createdAt)}
                </span>
              </div>
              <div className="cons-meta">
                {c.paymentStatus && (
                  <span className={`tag ${c.paymentStatus === 'paid' ? 'tag-good' : 'tag-warn'}`}>
                    {c.paymentStatus}
                  </span>
                )}
                <span className={`tag ${statusTag(c.status)}`}>{c.status ?? 'unknown'}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
