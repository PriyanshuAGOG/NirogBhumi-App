import { useEffect, useState } from 'react'
import {
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { errText } from '../lib/errors'
import { relativeTime } from '../lib/time'
import './Support.css'

interface ErrorReport {
  id: string
  userId?: string
  screen?: string
  message?: string
  code?: string | null
  resolved?: boolean
  createdAt?: unknown
  reviewedBy?: string
  reviewedAt?: unknown
}

function shortId(id: string | undefined): string {
  if (!id) return 'unknown'
  return id.length > 8 ? `${id.slice(0, 6)}…` : id
}

/**
 * Every CloudResult.Failure the Android app ever surfaces to a real user
 * also gets reported here (see MainActivity's single global cloudMessage
 * choke point) - this is the one place failures that only ever happen on
 * a real device are actually visible, instead of needing to be reproduced
 * blind from a bug description alone.
 */
export default function ErrorReports() {
  const { user } = useAuth()
  const [items, setItems] = useState<ErrorReport[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'open' | 'resolved' | 'all'>('open')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    // Ordered + bounded server-side (not fetch-everything-then-sort) - this
    // collection can grow fast since every real failure writes to it.
    const unsub = onSnapshot(
      query(collection(db, 'errorReports'), orderBy('createdAt', 'desc'), limit(300)),
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ErrorReport, 'id'>) })))
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load error reports'))
      },
    )
    return unsub
  }, [])

  const visible = items.filter((i) => {
    if (filter === 'all') return true
    if (filter === 'resolved') return i.resolved === true
    return i.resolved !== true
  })

  async function resolve(item: ErrorReport) {
    setBusyId(item.id)
    setActionError(null)
    try {
      await updateDoc(doc(db, 'errorReports', item.id), {
        resolved: true,
        reviewedBy: user?.uid ?? null,
        reviewedAt: serverTimestamp(),
      })
    } catch (err) {
      setActionError(errText(err, 'Could not mark resolved'))
    } finally {
      setBusyId(null)
    }
  }

  const openCount = items.filter((i) => i.resolved !== true).length

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Diagnostics</span>
        <h1>Error reports</h1>
        <p className="page-lede">
          Every failure the app actually surfaced to a real member, reported automatically - no
          need to reproduce a bug blind. {openCount} open right now.
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
          <p>Loading reports…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>✅</div>
          <p className="empty-title">
            {filter === 'open' ? 'Nothing open right now' : 'Nothing here'}
          </p>
          <p className="empty-sub">
            {filter === 'open'
              ? 'New failures from real devices will appear here in real time.'
              : `No ${filter} reports right now.`}
          </p>
        </div>
      ) : (
        <div className="sup-list">
          {visible.map((item) => (
            <article key={item.id} className="card sup-card">
              <div className="sup-head">
                <span className="sup-subject">{item.screen ?? 'unknown screen'}</span>
                <span className={`tag ${item.resolved ? 'tag-good' : 'tag-warn'}`}>
                  {item.resolved ? 'Resolved' : 'Open'}
                </span>
              </div>
              {item.message && <p className="sup-message">{item.message}</p>}
              <div className="sup-foot">
                <span className="sup-meta">
                  {shortId(item.userId)} · {relativeTime(item.createdAt)}
                </span>
                {!item.resolved && (
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
          ))}
        </div>
      )}
    </section>
  )
}
