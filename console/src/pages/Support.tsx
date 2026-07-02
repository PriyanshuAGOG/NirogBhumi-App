import { useEffect, useState } from 'react'
import {
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { errText } from '../lib/errors'
import { relativeTime, toDate } from '../lib/time'
import './Support.css'

interface SupportRequest {
  id: string
  userId?: string
  subject?: string
  message?: string
  status?: string
  createdAt?: unknown
  reviewedBy?: string
  reviewedAt?: unknown
}

function shortId(id: string | undefined): string {
  if (!id) return 'unknown'
  return id.length > 8 ? `${id.slice(0, 6)}…` : id
}

export default function Support() {
  const { user } = useAuth()
  const [items, setItems] = useState<SupportRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'open' | 'resolved' | 'all'>('open')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'supportRequests')),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<SupportRequest, 'id'>) }))
        next.sort(
          (a, b) => (toDate(b.createdAt)?.getTime() ?? 0) - (toDate(a.createdAt)?.getTime() ?? 0),
        )
        setItems(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load support requests'))
      },
    )
    return unsub
  }, [])

  const visible = items.filter((i) => {
    const status = i.status ?? 'open'
    if (filter === 'all') return true
    return status === filter
  })

  async function resolve(item: SupportRequest) {
    setBusyId(item.id)
    setActionError(null)
    try {
      await updateDoc(doc(db, 'supportRequests', item.id), {
        status: 'resolved',
        reviewedBy: user?.uid ?? null,
        reviewedAt: serverTimestamp(),
      })
    } catch (err) {
      setActionError(errText(err, 'Could not mark resolved'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Support</span>
        <h1>Support inbox</h1>
        <p className="page-lede">
          Questions members sent from the app. Read each, help out, then mark it resolved.
        </p>
      </header>

      <div className="toolbar">
        <div className="seg" role="tablist">
          {(['open', 'resolved', 'all'] as const).map((f) => (
            <button
              key={f}
              className={'seg-btn' + (filter === f ? ' seg-btn-active' : '')}
              onClick={() => setFilter(f)}
            >
              {f === 'open' ? 'Open' : f === 'resolved' ? 'Resolved' : 'All'}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}
      {actionError && (
        <div className="banner banner-error" role="alert">
          {actionError}
        </div>
      )}

      {loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading requests…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🌿</div>
          <p className="empty-title">
            {filter === 'open' ? 'Inbox zero — nothing open' : 'Nothing here'}
          </p>
          <p className="empty-sub">
            {filter === 'open'
              ? 'New member requests will appear here in real time.'
              : `No ${filter} requests right now.`}
          </p>
        </div>
      ) : (
        <div className="sup-list">
          {visible.map((item) => {
            const status = item.status ?? 'open'
            return (
              <article key={item.id} className="card sup-card">
                <div className="sup-head">
                  <span className="sup-subject">{item.subject ?? 'Support request'}</span>
                  <span className={`tag ${status === 'resolved' ? 'tag-good' : 'tag-warn'}`}>
                    {status === 'resolved' ? 'Resolved' : 'Open'}
                  </span>
                </div>
                {item.message && <p className="sup-message">{item.message}</p>}
                <div className="sup-foot">
                  <span className="sup-meta">
                    From {shortId(item.userId)} · {relativeTime(item.createdAt)}
                  </span>
                  {status !== 'resolved' && (
                    <button
                      className="btn btn-forest btn-sm"
                      disabled={busyId === item.id}
                      onClick={() => void resolve(item)}
                    >
                      {busyId === item.id ? 'Working…' : 'Mark resolved'}
                    </button>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
