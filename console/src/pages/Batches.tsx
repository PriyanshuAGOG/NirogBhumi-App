import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  addDoc,
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  where,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms, type Program } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { relativeTime, toDate, dayKey } from '../lib/time'
import './Batches.css'

interface Member {
  id: string
  programId?: string
  uid?: string
  name?: string
  joinedAt?: unknown
  status?: string
  lastCheckinAt?: unknown
  contributionKm?: number
}

interface BatchStat {
  checkedInCount?: number
  memberCount?: number
  collectiveKm?: number
}

/** Warm consistency read from a member's last check-in. */
function consistency(lastCheckinAt: unknown): { tag: string; cls: string; text: string } {
  const date = toDate(lastCheckinAt)
  if (!date) return { tag: 'Not started', cls: 'tag-neutral', text: 'No check-ins yet' }
  const days = (Date.now() - date.getTime()) / (24 * 60 * 60 * 1000)
  if (days <= 1.5) return { tag: 'Steady', cls: 'tag-good', text: `Checked in ${relativeTime(lastCheckinAt)}` }
  if (days <= 4) return { tag: 'Easing off', cls: 'tag-warn', text: `Last check-in ${relativeTime(lastCheckinAt)}` }
  return { tag: 'Quiet', cls: 'tag-bad', text: `Last check-in ${relativeTime(lastCheckinAt)}` }
}

function Roster({ program }: { program: Program }) {
  const { user, role } = useAuth()
  const isAdmin = role === 'admin' || role === 'super_admin'
  const [members, setMembers] = useState<Member[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [stat, setStat] = useState<BatchStat | null>(null)

  // Messaging modal state
  const [target, setTarget] = useState<Member | null>(null)
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)
  const [sentTo, setSentTo] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    const unsub = onSnapshot(
      query(collection(db, 'programMembers'), where('programId', '==', program.id)),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Member, 'id'>) }))
        next.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
        setMembers(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load the roster'))
      },
    )
    return unsub
  }, [program.id])

  useEffect(() => {
    if (!isAdmin) return
    const ref = doc(db, 'batchStats', `${program.id}_${dayKey()}`)
    const unsub = onSnapshot(
      ref,
      (snap) => setStat(snap.exists() ? (snap.data() as BatchStat) : null),
      () => setStat(null),
    )
    return unsub
  }, [program.id, isAdmin])

  async function send() {
    if (!target || !text.trim()) return
    setSending(true)
    setSendError(null)
    try {
      await addDoc(collection(db, 'coachMessages'), {
        programId: program.id,
        toUid: target.uid ?? target.id,
        fromUid: user?.uid ?? null,
        text: text.trim(),
        createdAt: serverTimestamp(),
      })
      setSentTo(target.name ?? 'member')
      setTarget(null)
      setText('')
    } catch (err) {
      setSendError(errText(err, 'Message could not be sent'))
    } finally {
      setSending(false)
    }
  }

  const pulse = stat ?? null
  const pulseMembers = pulse?.memberCount ?? program.memberCount ?? members.length

  return (
    <div className="roster">
      {isAdmin && (
        <div className="card pulse-card">
          <span className="overline">Batch pulse · today</span>
          <div className="pulse-row">
            <span className="pulse-big">
              {pulse ? `${pulse.checkedInCount ?? 0} of ${pulseMembers}` : `— of ${pulseMembers}`}
            </span>
            <span className="pulse-cap">batchmates checked in</span>
          </div>
          <div className="pulse-bar" aria-hidden>
            <span
              style={{
                width:
                  pulse && pulseMembers
                    ? `${Math.min(100, ((pulse.checkedInCount ?? 0) / pulseMembers) * 100)}%`
                    : '0%',
              }}
            />
          </div>
          {!pulse && (
            <p className="pulse-note">
              No pulse recorded for today yet — the daily aggregation writes it each morning.
            </p>
          )}
        </div>
      )}

      {sentTo && (
        <div className="banner banner-success" role="status">
          Your message to {sentTo} was sent.
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
          <p>Loading the roster…</p>
        </div>
      ) : members.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🌱</div>
          <p className="empty-title">No members enrolled yet</p>
          <p className="empty-sub">
            As people join {program.name ?? 'this batch'} with its program code, they will
            appear here.
          </p>
        </div>
      ) : (
        <div className="member-list">
          {members.map((m) => {
            const c = consistency(m.lastCheckinAt)
            return (
              <div key={m.id} className="card member-row">
                <div className="member-main">
                  <span className="member-name">{m.name ?? m.uid ?? 'Member'}</span>
                  <span className="member-sub">{c.text}</span>
                </div>
                <div className="member-meta">
                  {typeof m.contributionKm === 'number' && (
                    <span className="member-km">{m.contributionKm} km</span>
                  )}
                  <span className={`tag ${c.cls}`}>{c.tag}</span>
                  <Link to={`/members/${m.uid ?? m.id}`} className="btn btn-ghost btn-sm">
                    Health record
                  </Link>
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => {
                      setTarget(m)
                      setSentTo(null)
                      setSendError(null)
                    }}
                  >
                    Message
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {target && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setTarget(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <span className="overline">Private message</span>
            <h2 className="modal-title">Message {target.name ?? 'member'}</h2>
            <p className="modal-note">
              A quiet, private note to this member. Members never see this in the batch chat.
            </p>
            {sendError && (
              <div className="banner banner-error" role="alert">
                {sendError}
              </div>
            )}
            <textarea
              className="textarea"
              placeholder="How are things going this week?"
              value={text}
              onChange={(e) => setText(e.target.value)}
              autoFocus
            />
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setTarget(null)}>
                Cancel
              </button>
              <button
                className="btn btn-forest"
                disabled={sending || !text.trim()}
                onClick={() => void send()}
              >
                {sending ? 'Sending…' : 'Send message'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default function Batches() {
  const { programs, loading, error } = usePrograms()
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const selected = useMemo(
    () => programs.find((p) => p.id === selectedId) ?? null,
    [programs, selectedId],
  )

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Batches</span>
        <h1>{selected ? selected.name ?? 'Batch' : 'Your batches'}</h1>
        {!selected && (
          <p className="page-lede">
            Every Care+ cohort you run. Open a batch to see its roster, each member's
            rhythm, and the batch pulse.
          </p>
        )}
      </header>

      {selected && (
        <button className="btn btn-ghost btn-sm back-btn" onClick={() => setSelectedId(null)}>
          ← All batches
        </button>
      )}

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      {selected ? (
        <Roster program={selected} />
      ) : loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading batches…</p>
        </div>
      ) : programs.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🌿</div>
          <p className="empty-title">No batches yet</p>
          <p className="empty-sub">
            Create your first program from the Programs page to start a cohort.
          </p>
        </div>
      ) : (
        <div className="batch-list">
          {programs.map((p) => (
            <button key={p.id} className="card batch-card" onClick={() => setSelectedId(p.id)}>
              <div className="batch-main">
                <span className="batch-name">{p.name ?? 'Untitled program'}</span>
                <span className="batch-sub">
                  {p.coachName ? `Coach ${p.coachName}` : 'No coach assigned'}
                  {p.code ? ` · ${p.code}` : ''}
                </span>
              </div>
              <div className="batch-meta">
                <span className="batch-count">{p.memberCount ?? 0}</span>
                <span className="batch-count-cap">members</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
