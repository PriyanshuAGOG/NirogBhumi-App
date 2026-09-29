import { useEffect, useMemo, useState } from 'react'
import { collection, doc, getDoc, limit, onSnapshot, orderBy, query } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { errText } from '../lib/errors'
import { formatDate, relativeTime, toDate } from '../lib/time'
import { approveDeletion, rejectDeletion, startDeletion, type DeletionStatus } from '../lib/dataRequests'
import './Support.css'

interface DeletionRequest {
  id: string
  userId?: string
  userIdHash?: string
  status?: DeletionStatus
  source?: 'app' | 'admin' | 'email'
  scheduledFor?: unknown
  createdAt?: unknown
  completedAt?: unknown
  mode?: 'erased' | 'anonymized_health_retained'
  attempts?: number
  lastError?: string
}

type Pending = { kind: 'approve' | 'reject' | 'start'; request?: DeletionRequest; contact?: string } | null

const STATUS_META: Record<string, { label: string; tone: string }> = {
  scheduled: { label: 'Waiting (grace period)', tone: 'tag-warn' },
  requested: { label: 'Needs review', tone: 'tag-warn' },
  awaiting_verification: { label: 'Needs review', tone: 'tag-warn' },
  approved: { label: 'Approved — erasing soon', tone: 'tag-warn' },
  processing: { label: 'Erasing…', tone: 'tag-warn' },
  completed: { label: 'Completed', tone: 'tag-good' },
  cancelled: { label: 'Cancelled by member', tone: 'tag-neutral' },
  rejected: { label: 'Rejected', tone: 'tag-neutral' },
  failed: { label: 'Failed — needs attention', tone: 'tag-bad' },
}

const ACTIONABLE = new Set(['scheduled', 'requested', 'awaiting_verification', 'failed'])

/**
 * Account-deletion queue (DPDP erasure). Members request deletion in the app and
 * it runs on its own after a 7-day grace period; this page is where an admin can
 * see every request, skip the wait for a verified emailed request, or decline
 * one. Erasing an account is irreversible, so every action asks first.
 */
export default function DataRequests() {
  const [items, setItems] = useState<DeletionRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [names, setNames] = useState<Record<string, string>>({})
  const [filter, setFilter] = useState<'open' | 'done' | 'all'>('open')
  const [pending, setPending] = useState<Pending>(null)
  const [busy, setBusy] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [contactInput, setContactInput] = useState('')
  const [startOpen, setStartOpen] = useState(false)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'deletionRequests'), orderBy('createdAt', 'desc'), limit(100)),
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<DeletionRequest, 'id'>) })))
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load deletion requests'))
      },
    )
    return unsub
  }, [])

  // Show who each open request is for (completed ones no longer have a uid on purpose).
  useEffect(() => {
    const missing = items.filter((i) => i.userId && !names[i.userId]).map((i) => i.userId as string)
    if (!missing.length) return
    let cancelled = false
    void Promise.all(
      Array.from(new Set(missing)).map(async (uid) => {
        try {
          const snap = await getDoc(doc(db, 'users', uid))
          const label = snap.exists()
            ? [snap.get('fullName'), snap.get('email') ?? snap.get('phone')].filter(Boolean).join(' · ')
            : ''
          return [uid, label || 'Unknown member'] as const
        } catch {
          return [uid, 'Unknown member'] as const
        }
      }),
    ).then((pairs) => {
      if (!cancelled) setNames((prev) => ({ ...prev, ...Object.fromEntries(pairs) }))
    })
    return () => {
      cancelled = true
    }
  }, [items, names])

  const visible = useMemo(
    () =>
      items.filter((i) => {
        const finished = ['completed', 'cancelled', 'rejected'].includes(String(i.status))
        if (filter === 'all') return true
        return filter === 'done' ? finished : !finished
      }),
    [items, filter],
  )
  const openCount = items.filter((i) => !['completed', 'cancelled', 'rejected'].includes(String(i.status))).length

  async function confirm() {
    if (!pending) return
    setBusy(true)
    setModalError(null)
    try {
      if (pending.kind === 'approve') {
        await approveDeletion(pending.request?.id ?? '')
        setNotice('Approved. The account will be erased within the hour.')
      } else if (pending.kind === 'reject') {
        await rejectDeletion(pending.request?.id ?? '')
        setNotice('Request rejected. The member keeps their account.')
      } else {
        const contact = (pending.contact ?? '').trim()
        await startDeletion(contact.includes('@') ? { email: contact } : { phone: contact })
        setNotice('Deletion started. The account will be erased within the hour.')
        setContactInput('')
        setStartOpen(false)
      }
      setPending(null)
    } catch (err) {
      setModalError(errText(err, 'That did not go through'))
    } finally {
      setBusy(false)
    }
  }

  const who = (i: DeletionRequest) =>
    i.userId ? (names[i.userId] ?? 'Loading…') : i.userIdHash ? `Erased member (ref ${i.userIdHash.slice(0, 8)})` : 'Unknown'

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Privacy</span>
        <h1>Data requests</h1>
        <p className="page-lede">
          Account deletions requested by members (DPDP Act right to erasure). They run automatically after a
          7-day grace period. {openCount} open right now.
        </p>
      </header>

      <div className="toolbar">
        <div className="seg" role="tablist">
          {(['open', 'done', 'all'] as const).map((f) => (
            <button key={f} className={'seg-btn' + (filter === f ? ' seg-btn-active' : '')} onClick={() => setFilter(f)}>
              {f === 'open' ? 'Open' : f === 'done' ? 'Finished' : 'All'}
            </button>
          ))}
        </div>
        <span className="toolbar-spacer" />
        <button className="btn btn-ghost btn-sm" onClick={() => { setStartOpen((v) => !v); setModalError(null) }}>
          Start a deletion for an emailed request
        </button>
      </div>

      {startOpen && (
        <div className="card" style={{ display: 'grid', gap: 10 }}>
          <div className="field">
            <span className="field-label">Member's email or phone number</span>
            <input
              className="input"
              value={contactInput}
              onChange={(e) => setContactInput(e.target.value)}
              placeholder="name@example.com or +91…"
              inputMode="email"
            />
            <span className="field-hint">
              Only after you have verified the request came from the account holder. Skips the 7-day wait.
            </span>
          </div>
          <div>
            <button
              className="btn btn-forest btn-sm"
              disabled={!contactInput.trim()}
              onClick={() => { setModalError(null); setPending({ kind: 'start', contact: contactInput }) }}
            >
              Continue…
            </button>
          </div>
        </div>
      )}

      {notice && <div className="banner banner-success" role="status">{notice}</div>}
      {error && <div className="banner banner-error" role="alert">{error}</div>}

      {loading ? (
        <div className="card empty"><div className="spin spinner" aria-hidden /><p>Loading requests…</p></div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🔒</div>
          <p className="empty-title">{filter === 'open' ? 'No open deletion requests' : 'Nothing here'}</p>
          <p className="empty-sub">Requests from the app appear here in real time.</p>
        </div>
      ) : (
        <div className="sup-list">
          {visible.map((item) => {
            const meta = STATUS_META[String(item.status)] ?? { label: String(item.status ?? 'unknown'), tone: 'tag-neutral' }
            const runsAt = toDate(item.scheduledFor)
            return (
              <article key={item.id} className="card sup-card">
                <div className="sup-head">
                  <span className="sup-subject">{who(item)}</span>
                  <span className={`tag ${meta.tone}`}>{meta.label}</span>
                </div>
                {item.status === 'scheduled' && runsAt && (
                  <p className="sup-message">Will be erased on {formatDate(runsAt)} unless the member cancels.</p>
                )}
                {item.status === 'completed' && (
                  <p className="sup-message">
                    {item.mode === 'anonymized_health_retained'
                      ? 'Erased. Health readings kept without any link to the member (they opted into research).'
                      : 'Everything erased, including health readings.'}
                  </p>
                )}
                {item.lastError && <code className="sup-code">{item.lastError}</code>}
                <div className="sup-foot">
                  <span className="sup-meta">
                    {item.source === 'email' ? 'Emailed request' : item.source === 'admin' ? 'Started by admin' : 'From the app'} ·{' '}
                    {relativeTime(item.createdAt)}
                    {item.attempts ? ` · ${item.attempts} attempt${item.attempts === 1 ? '' : 's'}` : ''}
                  </span>
                  {ACTIONABLE.has(String(item.status)) && (
                    <span style={{ display: 'flex', gap: 8 }}>
                      {item.status !== 'failed' && (
                        <button className="btn btn-ghost btn-sm" onClick={() => { setModalError(null); setPending({ kind: 'reject', request: item }) }}>
                          Reject
                        </button>
                      )}
                      <button className="btn btn-forest btn-sm" onClick={() => { setModalError(null); setPending({ kind: 'approve', request: item }) }}>
                        {item.status === 'failed' ? 'Retry now' : 'Erase now'}
                      </button>
                    </span>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}

      {pending && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => !busy && setPending(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <span className="overline">{pending.kind === 'reject' ? 'Reject request' : 'Permanent action'}</span>
            <h2 className="modal-title">
              {pending.kind === 'reject'
                ? 'Reject this deletion request?'
                : pending.kind === 'approve'
                  ? `Erase ${pending.request ? who(pending.request) : 'this member'} now?`
                  : `Erase the account for ${(pending.contact ?? '').trim()}?`}
            </h2>
            <p className="page-lede">
              {pending.kind === 'reject'
                ? 'The member keeps their account and data. Let them know why.'
                : 'This skips the grace period. Their account, uploads, chats and login are permanently deleted within the hour and cannot be recovered.'}
            </p>
            {modalError && <div className="banner banner-error" role="alert">{modalError}</div>}
            <div className="modal-actions">
              <button className="btn btn-ghost" disabled={busy} onClick={() => setPending(null)}>Cancel</button>
              <button className="btn btn-forest" disabled={busy} onClick={() => void confirm()}>
                {busy ? 'Working…' : pending.kind === 'reject' ? 'Reject request' : 'Erase permanently'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
