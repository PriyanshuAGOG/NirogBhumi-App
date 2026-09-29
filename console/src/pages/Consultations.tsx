import { useEffect, useMemo, useState } from 'react'
import { collection, doc, getDoc, onSnapshot, orderBy, query, limit, serverTimestamp, Timestamp, updateDoc } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { errText } from '../lib/errors'
import { formatDateTime, fromInputDateTime, relativeTime, toDate, toInputDateTime } from '../lib/time'
import './Consultations.css'

type Mode = 'video' | 'phone' | 'in_person'

interface Consultation {
  id: string
  userId?: string
  consultationType?: string
  concern?: string
  preferredWindow?: string
  shareRecentLogs?: boolean
  status?: string
  paymentStatus?: string
  scheduledAt?: unknown
  expertName?: string
  mode?: Mode
  joinLink?: string | null
  location?: string | null
  feeNote?: string | null
  note?: string | null
  declineReason?: string | null
  cancelledBy?: string
  createdAt?: unknown
}

type Tab = 'requests' | 'upcoming' | 'past' | 'all'

const STATUS_LABEL: Record<string, { label: string; tone: string }> = {
  pending: { label: 'New request', tone: 'tag-warn' },
  payment_pending: { label: 'New request', tone: 'tag-warn' },
  confirmed: { label: 'Confirmed', tone: 'tag-good' },
  declined: { label: 'Declined', tone: 'tag-neutral' },
  cancelled: { label: 'Cancelled', tone: 'tag-neutral' },
  completed: { label: 'Completed', tone: 'tag-good' },
}
const MODE_LABEL: Record<Mode, string> = { video: 'Video call', phone: 'Phone call', in_person: 'In person' }
const isOpenRequest = (c: Consultation) => c.status === 'pending' || c.status === 'payment_pending'

interface ConfirmDraft {
  id: string
  reschedule: boolean
  when: string
  expert: string
  mode: Mode
  where: string
  fee: string
  note: string
}

function safeHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

type Action = { kind: 'decline' | 'cancel' | 'complete'; item: Consultation } | null

/**
 * Consultation requests from the app. A member asks for a time; staff confirm one here
 * (expert, date and time, how to join, any fee note) and the member is notified and
 * reminded an hour before automatically. Fees are arranged by the team - nothing here
 * takes payment.
 */
export default function Consultations() {
  const { user } = useAuth()
  const [items, setItems] = useState<Consultation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('requests')
  const [people, setPeople] = useState<Record<string, { name: string; contact: string }>>({})
  const [confirm, setConfirm] = useState<ConfirmDraft | null>(null)
  const [action, setAction] = useState<Action>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'consultations'), orderBy('createdAt', 'desc'), limit(300)),
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Consultation, 'id'>) })))
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

  useEffect(() => {
    const missing = Array.from(new Set(items.map((i) => i.userId).filter((u): u is string => !!u && !people[u])))
    if (!missing.length) return
    let cancelled = false
    void Promise.all(
      missing.map(async (uid) => {
        try {
          const snap = await getDoc(doc(db, 'users', uid))
          return [uid, { name: String(snap.get('fullName') ?? '').trim() || 'Member', contact: String(snap.get('phone') ?? snap.get('email') ?? '') }] as const
        } catch {
          return [uid, { name: 'Member', contact: '' }] as const
        }
      }),
    ).then((pairs) => {
      if (!cancelled) setPeople((prev) => ({ ...prev, ...Object.fromEntries(pairs) }))
    })
    return () => {
      cancelled = true
    }
  }, [items, people])

  const now = Date.now()
  const visible = useMemo(() => {
    const at = (c: Consultation) => toDate(c.scheduledAt)?.getTime() ?? 0
    const list = items.filter((c) => {
      if (tab === 'requests') return isOpenRequest(c)
      if (tab === 'upcoming') return c.status === 'confirmed' && at(c) >= now - 60 * 60 * 1000
      if (tab === 'past') return !isOpenRequest(c) && !(c.status === 'confirmed' && at(c) >= now - 60 * 60 * 1000)
      return true
    })
    // Requests: oldest first (longest waiting); upcoming: soonest first; rest newest first.
    if (tab === 'requests') return [...list].sort((a, b) => (toDate(a.createdAt)?.getTime() ?? 0) - (toDate(b.createdAt)?.getTime() ?? 0))
    if (tab === 'upcoming') return [...list].sort((a, b) => at(a) - at(b))
    return list
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, tab])
  const waiting = items.filter(isOpenRequest).length

  const who = (c: Consultation) => (c.userId ? (people[c.userId]?.name ?? '…') : 'Member')
  const contactOf = (c: Consultation) => (c.userId ? people[c.userId]?.contact : '')

  function openConfirm(c: Consultation) {
    const existing = toDate(c.scheduledAt)
    setModalError(null)
    setConfirm({
      id: c.id,
      reschedule: c.status === 'confirmed',
      when: existing ? toInputDateTime(existing) : '',
      expert: c.expertName ?? '',
      mode: c.mode ?? 'video',
      where: (c.mode === 'in_person' ? c.location : c.joinLink) ?? '',
      fee: c.feeNote ?? '',
      note: c.note ?? '',
    })
  }

  async function saveConfirm() {
    if (!confirm) return
    const when = fromInputDateTime(confirm.when)
    if (!when) return setModalError('Pick the date and time.')
    if (when.getTime() < Date.now() - 5 * 60_000) return setModalError('That time is in the past.')
    if (!confirm.expert.trim()) return setModalError("Add the expert's name.")
    const where = confirm.where.trim()
    if (confirm.mode === 'video' && !safeHttpUrl(where)) return setModalError('Add the video link (it must start with https://).')
    if (confirm.mode === 'phone' && !where) return setModalError('Add the number the expert will call from, or a short note.')
    if (confirm.mode === 'in_person' && !where) return setModalError('Add the address or place to meet.')
    setBusy(true)
    setModalError(null)
    try {
      await updateDoc(doc(db, 'consultations', confirm.id), {
        status: 'confirmed',
        scheduledAt: Timestamp.fromDate(when),
        expertName: confirm.expert.trim(),
        mode: confirm.mode,
        joinLink: confirm.mode === 'video' ? where : null,
        location: confirm.mode === 'video' ? null : where,
        feeNote: confirm.fee.trim() || null,
        note: confirm.note.trim() || null,
        declineReason: null,
        confirmedBy: user?.uid ?? null,
        confirmedAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
      setNotice(confirm.reschedule ? 'Updated. The member has been notified of the new time.' : 'Confirmed. The member has been notified and will be reminded an hour before.')
      setConfirm(null)
    } catch (err) {
      setModalError(errText(err, 'Could not confirm this consultation'))
    } finally {
      setBusy(false)
    }
  }

  async function runAction() {
    if (!action) return
    if (action.kind === 'decline' && !reason.trim()) return setModalError('Tell the member why, and what to do next.')
    setBusy(true)
    setModalError(null)
    try {
      const patch =
        action.kind === 'decline'
          ? { status: 'declined', declineReason: reason.trim().slice(0, 300) }
          : action.kind === 'cancel'
            ? { status: 'cancelled', cancelledBy: 'staff' }
            : { status: 'completed' }
      await updateDoc(doc(db, 'consultations', action.item.id), { ...patch, updatedAt: serverTimestamp() })
      setNotice(action.kind === 'decline' ? 'Declined. The member has been told.' : action.kind === 'cancel' ? 'Cancelled. The member has been told.' : 'Marked as completed.')
      setAction(null)
      setReason('')
    } catch (err) {
      setModalError(errText(err, 'That did not go through'))
    } finally {
      setBusy(false)
    }
  }

  const TABS: { key: Tab; label: string }[] = [
    { key: 'requests', label: waiting ? `Requests (${waiting})` : 'Requests' },
    { key: 'upcoming', label: 'Upcoming' },
    { key: 'past', label: 'Past' },
    { key: 'all', label: 'All' },
  ]

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Care</span>
        <h1>Consultations</h1>
        <p className="page-lede">
          Requests from members. Confirm a time and they're notified and reminded an hour before.
          Fees are arranged by the team - nothing here takes payment. {waiting} waiting right now.
        </p>
      </header>

      <div className="toolbar">
        <div className="seg" role="tablist">
          {TABS.map((t) => (
            <button key={t.key} className={'seg-btn' + (tab === t.key ? ' seg-btn-active' : '')} onClick={() => setTab(t.key)}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {notice && <div className="banner banner-success" role="status">{notice}</div>}
      {error && <div className="banner banner-error" role="alert">{error}</div>}

      {loading ? (
        <div className="card empty"><div className="spin spinner" aria-hidden /><p>Loading consultations…</p></div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🩺</div>
          <p className="empty-title">{tab === 'requests' ? 'No requests waiting' : 'Nothing here'}</p>
          <p className="empty-sub">{tab === 'requests' ? 'New requests from the app appear here in real time.' : 'Nothing matches this view.'}</p>
        </div>
      ) : (
        <div className="cons-list">
          {visible.map((c) => {
            const meta = STATUS_LABEL[c.status ?? ''] ?? { label: c.status ?? 'unknown', tone: 'tag-neutral' }
            const when = toDate(c.scheduledAt)
            return (
              <article key={c.id} className="card cons-card">
                <div className="cons-top">
                  <div className="cons-main">
                    <span className="cons-type">
                      {c.consultationType ?? 'Consultation'}
                    </span>
                    <span className="cons-sub">
                      {who(c)}{contactOf(c) ? ` · ${contactOf(c)}` : ''} · asked {relativeTime(c.createdAt)}
                    </span>
                  </div>
                  <span className={`tag ${meta.tone}`}>{meta.label}</span>
                </div>
                {c.concern && <p className="cons-concern">“{c.concern}”</p>}
                <div className="cons-facts">
                  {c.preferredWindow && c.preferredWindow !== 'Any' && <span>Prefers {c.preferredWindow.toLowerCase()}</span>}
                  {c.shareRecentLogs && <span>Shares recent readings</span>}
                  {c.userId && (
                    <a href={`/members/${c.userId}`} className="cons-link">Open health record</a>
                  )}
                </div>
                {c.status === 'confirmed' && when && (
                  <div className="cons-when">
                    <strong>{formatDateTime(when)}</strong>
                    {' · '}{c.expertName}{c.mode ? ` · ${MODE_LABEL[c.mode]}` : ''}
                    {c.feeNote ? ` · ${c.feeNote}` : ''}
                  </div>
                )}
                {c.status === 'declined' && c.declineReason && <p className="cons-concern">Declined: {c.declineReason}</p>}
                <div className="cons-actions">
                  {isOpenRequest(c) && (
                    <>
                      <button className="btn btn-ghost btn-sm" onClick={() => { setModalError(null); setReason(''); setAction({ kind: 'decline', item: c }) }}>Decline</button>
                      <button className="btn btn-forest btn-sm" onClick={() => openConfirm(c)}>Confirm a time</button>
                    </>
                  )}
                  {c.status === 'confirmed' && (
                    <>
                      <button className="btn btn-ghost btn-sm" onClick={() => { setModalError(null); setAction({ kind: 'cancel', item: c }) }}>Cancel</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => openConfirm(c)}>Reschedule</button>
                      <button className="btn btn-forest btn-sm" onClick={() => { setModalError(null); setAction({ kind: 'complete', item: c }) }}>Mark completed</button>
                    </>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}

      {confirm && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => !busy && setConfirm(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <span className="overline">{confirm.reschedule ? 'Reschedule' : 'Confirm'}</span>
            <h2 className="modal-title">{confirm.reschedule ? 'Change the appointment' : 'Confirm a time'}</h2>
            {modalError && <div className="banner banner-error" role="alert">{modalError}</div>}
            <div className="field-row">
              <div className="field">
                <span className="field-label">Date and time</span>
                <input className="input" type="datetime-local" value={confirm.when} onChange={(e) => setConfirm({ ...confirm, when: e.target.value })} />
              </div>
              <div className="field">
                <span className="field-label">Expert</span>
                <input className="input" value={confirm.expert} onChange={(e) => setConfirm({ ...confirm, expert: e.target.value })} placeholder="Dr. Meera" />
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <span className="field-label">How</span>
                <select className="select" value={confirm.mode} onChange={(e) => setConfirm({ ...confirm, mode: e.target.value as Mode })}>
                  <option value="video">Video call</option>
                  <option value="phone">Phone call</option>
                  <option value="in_person">In person</option>
                </select>
              </div>
              <div className="field">
                <span className="field-label">{confirm.mode === 'video' ? 'Video link' : confirm.mode === 'phone' ? 'Number / note' : 'Address'}</span>
                <input className="input" value={confirm.where} onChange={(e) => setConfirm({ ...confirm, where: e.target.value })} placeholder={confirm.mode === 'video' ? 'https://meet.…' : ''} />
              </div>
            </div>
            <div className="field">
              <span className="field-label">Fee note (optional)</span>
              <input className="input" value={confirm.fee} onChange={(e) => setConfirm({ ...confirm, fee: e.target.value })} placeholder="e.g. ₹699 - we'll send a payment link" />
            </div>
            <div className="field">
              <span className="field-label">Message to the member (optional)</span>
              <textarea className="textarea" rows={3} value={confirm.note} onChange={(e) => setConfirm({ ...confirm, note: e.target.value })} placeholder="Keep your last week's readings handy." />
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" disabled={busy} onClick={() => setConfirm(null)}>Cancel</button>
              <button className="btn btn-forest" disabled={busy} onClick={() => void saveConfirm()}>{busy ? 'Saving…' : confirm.reschedule ? 'Save new time' : 'Confirm & notify'}</button>
            </div>
          </div>
        </div>
      )}

      {action && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => !busy && setAction(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">
              {action.kind === 'decline' ? 'Decline this request?' : action.kind === 'cancel' ? 'Cancel this appointment?' : 'Mark as completed?'}
            </h2>
            {modalError && <div className="banner banner-error" role="alert">{modalError}</div>}
            {action.kind === 'decline' && (
              <div className="field">
                <span className="field-label">What should the member know?</span>
                <textarea className="textarea" rows={3} value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} placeholder="We're fully booked this week - please request again from Monday." autoFocus />
              </div>
            )}
            {action.kind === 'cancel' && <p className="page-lede">The member is told, and their reminder is removed.</p>}
            <div className="modal-actions">
              <button className="btn btn-ghost" disabled={busy} onClick={() => setAction(null)}>Back</button>
              <button className="btn btn-forest" disabled={busy} onClick={() => void runAction()}>
                {busy ? 'Working…' : action.kind === 'decline' ? 'Decline & notify' : action.kind === 'cancel' ? 'Cancel appointment' : 'Mark completed'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
