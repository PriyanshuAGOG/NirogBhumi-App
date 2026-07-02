import { useEffect, useMemo, useState } from 'react'
import {
  collection,
  doc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
  where,
  type DocumentData,
  type QuerySnapshot,
} from 'firebase/firestore'
import { FirebaseError } from 'firebase/app'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { relativeTime } from '../lib/time'
import './Moderation.css'

interface Report {
  id: string
  messageId?: string
  programId?: string
  reportedText?: string
  reportedUserId?: string
  reporterId?: string
  createdAt?: unknown
  status?: string
}

type ActionKind = 'dismissed' | 'actioned'

function shortId(id: string | undefined): string {
  if (!id) return 'unknown'
  return id.length > 8 ? `${id.slice(0, 6)}…` : id
}

export default function Moderation() {
  const { user } = useAuth()
  const [reports, setReports] = useState<Report[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  // Docs the user has just acted on — hidden optimistically before the
  // snapshot round-trips.
  const [resolvedIds, setResolvedIds] = useState<Set<string>>(new Set())
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set())
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    const col = collection(db, 'reportedMessages')
    // Prefer open reports, newest first. Requires a composite index on
    // (status ASC, createdAt DESC) — a fallback listener kicks in if the
    // filtered query fails (e.g. missing index or absent `status` field).
    const openQuery = query(
      col,
      where('status', '==', 'open'),
      orderBy('createdAt', 'desc'),
    )

    const handleSnapshot = (snap: QuerySnapshot<DocumentData>) => {
      const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Report, 'id'>) }))
      setReports(next)
      setLoading(false)
      setLoadError(null)
    }

    let unsubFallback: (() => void) | null = null
    const startFallback = () => {
      if (unsubFallback) return
      const allQuery = query(col, orderBy('createdAt', 'desc'))
      unsubFallback = onSnapshot(
        allQuery,
        (snap) => {
          const next = snap.docs
            .map((d) => ({ id: d.id, ...(d.data() as Omit<Report, 'id'>) }))
            // Treat a missing status as still-open.
            .filter((r) => r.status === undefined || r.status === 'open')
          setReports(next)
          setLoading(false)
          setLoadError(null)
        },
        (err) => {
          setLoading(false)
          setLoadError(
            err instanceof FirebaseError
              ? `Could not load reports (${err.code}).`
              : 'Could not load reports.',
          )
        },
      )
    }

    const unsubPrimary = onSnapshot(openQuery, handleSnapshot, () => {
      // Filtered query failed — fall back to the unfiltered listener.
      startFallback()
    })

    return () => {
      unsubPrimary()
      if (unsubFallback) unsubFallback()
    }
  }, [])

  const visible = useMemo(
    () => reports.filter((r) => !resolvedIds.has(r.id)),
    [reports, resolvedIds],
  )

  async function act(report: Report, kind: ActionKind) {
    setActionError(null)
    setBusyIds((prev) => new Set(prev).add(report.id))
    // Optimistic: hide immediately.
    setResolvedIds((prev) => new Set(prev).add(report.id))
    try {
      await updateDoc(doc(db, 'reportedMessages', report.id), {
        status: kind,
        reviewedBy: user?.uid ?? null,
        reviewedAt: serverTimestamp(),
      })
    } catch (err) {
      // Roll back the optimistic hide and surface the error.
      setResolvedIds((prev) => {
        const next = new Set(prev)
        next.delete(report.id)
        return next
      })
      setActionError(
        err instanceof FirebaseError
          ? `Action failed (${err.code}). Please try again.`
          : 'Action failed. Please try again.',
      )
    } finally {
      setBusyIds((prev) => {
        const next = new Set(prev)
        next.delete(report.id)
        return next
      })
    }
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Moderation</span>
        <h1>Reported messages</h1>
        <p className="mod-lede">
          Live queue of messages members flagged in program chat. Review each and
          dismiss or remove.
        </p>
      </header>

      {loadError && (
        <div className="mod-banner mod-banner-error" role="alert">
          {loadError}
        </div>
      )}
      {actionError && (
        <div className="mod-banner mod-banner-error" role="alert">
          {actionError}
        </div>
      )}

      {loading ? (
        <div className="card mod-empty">
          <div className="spin mod-spinner" aria-hidden />
          <p>Loading the queue…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card mod-empty">
          <div className="mod-empty-mark" aria-hidden>🌿</div>
          <p className="mod-empty-title">No open reports — the community is healthy.</p>
          <p className="mod-empty-sub">
            New reports appear here in real time as members flag messages.
          </p>
        </div>
      ) : (
        <div className="mod-list">
          {visible.map((report) => {
            const busy = busyIds.has(report.id)
            return (
              <article key={report.id} className="card mod-card">
                <div className="mod-card-meta">
                  <span className="mod-chip">Program {shortId(report.programId)}</span>
                  <span className="mod-time">{relativeTime(report.createdAt)}</span>
                </div>

                <blockquote className="mod-text">
                  {report.reportedText?.trim()
                    ? report.reportedText
                    : '(no message text captured)'}
                </blockquote>

                <div className="mod-attrib">
                  <span>
                    Reported by <strong>{shortId(report.reporterId)}</strong>
                  </span>
                  <span className="mod-dot" aria-hidden>·</span>
                  <span>
                    Author <strong>{shortId(report.reportedUserId)}</strong>
                  </span>
                </div>

                <div className="mod-actions">
                  <button
                    className="btn btn-ghost"
                    disabled={busy}
                    onClick={() => void act(report, 'dismissed')}
                  >
                    Dismiss
                  </button>
                  <button
                    className="btn btn-danger"
                    disabled={busy}
                    onClick={() => void act(report, 'actioned')}
                  >
                    {busy ? 'Working…' : 'Remove message'}
                  </button>
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
