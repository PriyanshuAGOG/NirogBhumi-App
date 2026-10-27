import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  addDoc,
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  where,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { chunk } from '../lib/usePrograms'
import { useScope } from '../lib/scope'
import { errText } from '../lib/errors'
import { relativeTime, formatDateTime } from '../lib/time'
import { consistencyTag, isAlertLog, isImported, LOG_COLLECTIONS, summarizeLog, type LogEntry, type LogKind } from '../lib/health'
import { buildProfileSections } from '../lib/memberProfile'
import type { InboxMessage } from '../lib/inbox'
import ThreadPanel from './ThreadPanel'
import './MemberDetail.css'

interface MemberProfile {
  name?: string
  fullName?: string
  email?: string
  phone?: string
  role?: string
  programActive?: boolean
  activeProgramId?: string
  activeProgramName?: string
  latestMetrics?: { fastingSugar?: number; glucoseStatus?: string; glucoseUpdatedAt?: unknown }
  [key: string]: unknown
}

interface RosterEntry {
  id: string
  programId?: string
  joinedAt?: unknown
  lastCheckinAt?: unknown
}

interface CoachNote {
  id: string
  authorEmail?: string
  text?: string
  createdAt?: unknown
}

/** One member's logs from one collection, newest first, capped for a console read. */
function useLogFeed(kind: LogKind, uid: string | undefined, max = 20): { entries: LogEntry[]; error: boolean } {
  const [entries, setEntries] = useState<LogEntry[]>([])
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!uid) return
    const unsub = onSnapshot(
      query(collection(db, kind), where('userId', '==', uid), orderBy('createdAt', 'desc'), limit(max)),
      (snap) => {
        setEntries(snap.docs.map((d) => ({ id: d.id, kind, at: d.data().createdAt, data: d.data() })))
        setError(false)
      },
      () => setError(true),
    )
    return unsub
    // kind is a stable literal per call site; only uid and the limit change across renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid, max])

  return { entries, error }
}

/** A member's documents from a collection that has no ordered index: equality filter only, newest first on the page. */
function useUserFeed(name: 'consultations' | 'supportRequests', uid: string | undefined): { rows: Record<string, unknown>[]; error: boolean } {
  const [rows, setRows] = useState<Record<string, unknown>[]>([])
  const [error, setError] = useState(false)
  useEffect(() => {
    if (!uid) return
    const unsub = onSnapshot(
      query(collection(db, name), where('userId', '==', uid), limit(100)),
      (snap) => {
        const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }) as Record<string, unknown>)
        list.sort((a, b) => ((b.createdAt as { seconds?: number })?.seconds ?? 0) - ((a.createdAt as { seconds?: number })?.seconds ?? 0))
        setRows(list)
        setError(false)
      },
      () => setError(true),
    )
    return unsub
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid])
  return { rows, error }
}

/** This member's private thread with their coach (the app's "Ask your coach"), oldest first. */
function useThread(programId: string | undefined, uid: string | undefined): { messages: InboxMessage[]; error: boolean } {
  const [messages, setMessages] = useState<InboxMessage[]>([])
  const [error, setError] = useState(false)
  useEffect(() => {
    if (!uid || !programId) {
      setMessages([])
      return
    }
    const unsub = onSnapshot(
      query(collection(db, 'coachInboxMessages'), where('programId', '==', programId), where('memberUid', '==', uid), orderBy('createdAt', 'desc'), limit(100)),
      (snap) => {
        setMessages(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<InboxMessage, 'id'>) })).reverse())
        setError(false)
      },
      () => setError(true),
    )
    return unsub
  }, [uid, programId])
  return { messages, error }
}

export default function MemberDetail() {
  const { uid } = useParams<{ uid: string }>()
  const { user } = useAuth()
  const { programs, programIds, programKey, isAdmin, loading: scopeLoading } = useScope()
  const [profile, setProfile] = useState<MemberProfile | null>(null)
  const [profileLoading, setProfileLoading] = useState(true)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [roster, setRoster] = useState<RosterEntry[]>([])
  const [notes, setNotes] = useState<CoachNote[]>([])
  const [noteText, setNoteText] = useState('')
  const [noteBusy, setNoteBusy] = useState(false)
  const [noteError, setNoteError] = useState<string | null>(null)
  const [tab, setTab] = useState<'overview' | 'profile' | 'records' | 'messages' | 'care' | 'notes'>('overview')
  const [limits, setLimits] = useState<Partial<Record<LogKind, number>>>({})
  
  useEffect(() => {
    if (!uid) return
    const unsub = onSnapshot(
      doc(db, 'users', uid),
      (snap) => {
        setProfile(snap.exists() ? (snap.data() as MemberProfile) : null)
        setProfileLoading(false)
        setProfileError(null)
      },
      (err) => {
        setProfileLoading(false)
        setProfileError(errText(err, 'Could not load this member'))
      },
    )
    return unsub
  }, [uid])

  useEffect(() => {
    if (!uid) return
    // A coach may only list roster rows of programs they coach, so the query has
    // to say so; an admin reads all of this member's roster entries.
    if (!isAdmin && (scopeLoading || programIds.length === 0)) {
      setRoster([])
      return
    }
    const queries = isAdmin
      ? [query(collection(db, 'programMembers'), where('uid', '==', uid))]
      : chunk(programIds).map((ids) => query(collection(db, 'programMembers'), where('uid', '==', uid), where('programId', 'in', ids)))
    const perChunk = new Map<number, RosterEntry[]>()
    const unsubs = queries.map((q, index) =>
      onSnapshot(
        q,
        (snap) => {
          perChunk.set(index, snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<RosterEntry, 'id'>) })))
          setRoster(Array.from(perChunk.values()).flat())
        },
        () => setRoster([]),
      ),
    )
    return () => unsubs.forEach((u) => u())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid, isAdmin, programKey, scopeLoading])

  useEffect(() => {
    if (!uid) return
    const unsub = onSnapshot(
      query(collection(db, 'coachNotes'), where('targetUid', '==', uid), orderBy('createdAt', 'desc')),
      (snap) => setNotes(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<CoachNote, 'id'>) }))),
    )
    return unsub
  }, [uid])

  const glucose = useLogFeed('glucoseReadings', uid, limits.glucoseReadings ?? 20)
  const bp = useLogFeed('bpReadings', uid, limits.bpReadings ?? 20)
  const weight = useLogFeed('weightLogs', uid, limits.weightLogs ?? 20)
  const medication = useLogFeed('medicationLogs', uid, limits.medicationLogs ?? 20)
  const sleep = useLogFeed('sleepLogs', uid, limits.sleepLogs ?? 20)
  const walk = useLogFeed('walkLogs', uid, limits.walkLogs ?? 20)
  const labs = useLogFeed('labReports', uid, limits.labReports ?? 20)
  const checklist = useLogFeed('checklistLogs', uid, limits.checklistLogs ?? 20)
  const checkins = useLogFeed('dailyCheckins', uid, limits.dailyCheckins ?? 20)
  const feeds = useMemo(
    () => ({ glucoseReadings: glucose, bpReadings: bp, weightLogs: weight, medicationLogs: medication, sleepLogs: sleep, walkLogs: walk, labReports: labs, checklistLogs: checklist, dailyCheckins: checkins }),
    [glucose, bp, weight, medication, sleep, walk, labs, checklist, checkins],
  )

  const timeline = useMemo(() => {
    const all = Object.values(feeds).flatMap((f) => f.entries)
    all.sort((a, b) => {
      const ta = (a.at as { seconds?: number })?.seconds ?? 0
      const tb = (b.at as { seconds?: number })?.seconds ?? 0
      return tb - ta
    })
    return all
  }, [feeds])

  const lastActivity = timeline[0]?.at
  const alerts = useMemo(() => timeline.filter(isAlertLog).slice(0, 5), [timeline])

  const primaryProgramId = roster[0]?.programId ?? profile?.activeProgramId
  const programName = programs.find((p) => p.id === primaryProgramId)?.name ?? profile?.activeProgramName
  const thread = useThread(primaryProgramId, uid)
  const consults = useUserFeed('consultations', uid)
  const support = useUserFeed('supportRequests', uid)
  const awaitingReply = thread.messages.length > 0 && thread.messages[thread.messages.length - 1].fromUid === uid

  async function sendNote() {
    if (!uid || !noteText.trim()) return
    setNoteBusy(true)
    setNoteError(null)
    try {
      await addDoc(collection(db, 'coachNotes'), {
        targetUid: uid,
        authorId: user?.uid ?? null,
        authorEmail: user?.email ?? null,
        text: noteText.trim(),
        createdAt: serverTimestamp(),
      })
      setNoteText('')
    } catch (err) {
      setNoteError(errText(err, 'Note could not be saved'))
    } finally {
      setNoteBusy(false)
    }
  }

  if (profileLoading) {
    return (
      <section className="page">
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading member…</p>
        </div>
      </section>
    )
  }

  if (profileError || !profile) {
    return (
      <section className="page">
        <Link to="/members" className="btn btn-ghost btn-sm back-btn">
          ← All members
        </Link>
        <div className="banner banner-error" role="alert">
          {profileError ?? 'This member could not be found.'}
        </div>
      </section>
    )
  }

  const c = consistencyTag(lastActivity)
  const name = profile.fullName ?? profile.name ?? 'Member'

  return (
    <section className="page">
      <Link to="/members" className="btn btn-ghost btn-sm back-btn">
        ← All members
      </Link>

      <header className="page-head md-head">
        <div>
          <span className="overline">Member</span>
          <h1>{name}</h1>
          <p className="page-lede">
            {profile.email ?? '—'}
            {profile.phone ? ` · ${profile.phone}` : ''}
            {programName ? ` · ${programName}` : ''}
          </p>
        </div>
        <span className={`tag md-tag ${c.cls}`}>{c.tag}</span>
      </header>

      {alerts.length > 0 && (
        <div className="card md-alert-card">
          <span className="overline">Needs a look</span>
          <div className="md-alert-list">
            {alerts.map((a) => (
              <div key={a.id} className="md-alert-row">
                <span>{LOG_COLLECTIONS.find((l) => l.kind === a.kind)?.icon}</span>
                <span className="md-alert-text">{summarizeLog(a)}</span>
                <span className="md-alert-time">{relativeTime(a.at)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="seg md-tabs" role="tablist">
        {(
          [
            ['overview', 'Overview'],
            ['profile', 'Profile'],
            ['records', 'Health records'],
            ['messages', awaitingReply ? 'Messages • needs a reply' : 'Messages'],
            ['care', 'Care & support'],
            ['notes', `Notes (${notes.length})`],
          ] as const
        ).map(([t, label]) => (
          <button key={t} className={'seg-btn' + (tab === t ? ' seg-btn-active' : '')} onClick={() => setTab(t)}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <>
          <div className="md-overview-grid">
            <div className="card md-stat-card">
              <span className="tile-label">Fasting blood sugar (latest)</span>
              <span className="md-stat-value">
                {profile.latestMetrics?.fastingSugar != null ? `${profile.latestMetrics.fastingSugar} mg/dL` : '—'}
              </span>
              <span className="tile-hint">
                {profile.latestMetrics?.glucoseUpdatedAt ? relativeTime(profile.latestMetrics.glucoseUpdatedAt) : 'No fasting reading yet'}
              </span>
            </div>
            <div className="card md-stat-card">
              <span className="tile-label">Last activity</span>
              <span className="md-stat-value">{lastActivity ? relativeTime(lastActivity) : '—'}</span>
              <span className="tile-hint">{lastActivity ? formatDateTime(lastActivity) : 'No logs yet'}</span>
            </div>
            <div className="card md-stat-card">
              <span className="tile-label">Program</span>
              <span className="md-stat-value md-stat-text">{programName ?? 'Not enrolled'}</span>
              <span className="tile-hint">
                {roster[0]?.joinedAt ? `Joined ${formatDateTime(roster[0].joinedAt)}` : profile.programActive ? 'Active' : 'No active program'}
              </span>
            </div>
            <div className="card md-stat-card">
              <span className="tile-label">Entries loaded</span>
              <span className="md-stat-value">{timeline.length}</span>
              <span className="tile-hint">Open Health records for the full history</span>
            </div>
          </div>
          <div className="card md-latest">
            <span className="overline">Latest readings</span>
            {(['glucoseReadings', 'bpReadings', 'weightLogs', 'sleepLogs', 'walkLogs'] as LogKind[]).map((kind) => {
              const meta = LOG_COLLECTIONS.find((l) => l.kind === kind)
              const latest = feeds[kind].entries[0]
              return (
                <div key={kind} className="md-latest-row">
                  <span aria-hidden>{meta?.icon}</span>
                  <span className="md-latest-label">{meta?.label}</span>
                  <span className="md-latest-value">{latest ? summarizeLog(latest) : 'Nothing logged'}</span>
                  <span className="md-latest-time">{latest ? relativeTime(latest.at) : ''}</span>
                </div>
              )
            })}
          </div>
        </>
      )}

      {tab === 'profile' && (
        <div className="md-profile">
          {buildProfileSections(profile).map((section) => (
            <div key={section.title} className="card md-profile-card">
              <span className="overline">{section.title}</span>
              <dl className="md-kv">
                {section.rows.map((row) => (
                  <div key={row.label} className="md-kv-row">
                    <dt>{row.label}</dt>
                    <dd>{row.value}</dd>
                  </div>
                ))}
              </dl>
            </div>
          ))}
        </div>
      )}

      {tab === 'records' && (
        <div className="md-records">
          {LOG_COLLECTIONS.map((l) => {
            const feed = feeds[l.kind]
            const max = limits[l.kind] ?? 20
            return (
              <div key={l.kind} className="card md-records-card">
                <div className="md-records-head">
                  <span className="overline">
                    {l.icon} {l.label}
                  </span>
                  <span className="md-muted">{feed.error ? 'Not available' : feed.entries.length >= max ? `Latest ${feed.entries.length}` : `${feed.entries.length} on record`}</span>
                </div>
                {feed.error ? (
                  <p className="md-muted">You don’t have access to this category.</p>
                ) : feed.entries.length === 0 ? (
                  <p className="md-muted">Nothing logged yet.</p>
                ) : (
                  <table className="md-table">
                    <tbody>
                      {feed.entries.map((e) => (
                        <tr key={e.id}>
                          <td className="md-td-time">{formatDateTime(e.at)}</td>
                          <td>{summarizeLog(e)}</td>
                          <td className="md-td-tags">
                            {isImported(e.data) && <span className="tag tag-neutral">From a device</span>}
                            {Boolean(e.data.lastCorrectedAt) && <span className="tag tag-neutral">Corrected</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
                {!feed.error && feed.entries.length >= max && max < 300 && (
                  <button className="btn btn-ghost btn-sm" onClick={() => setLimits((prev) => ({ ...prev, [l.kind]: Math.min(300, max + 40) }))}>
                    Show 40 more
                  </button>
                )}
              </div>
            )
          })}
        </div>
      )}

      {tab === 'messages' && (
        <div className="card md-messages">
          {!primaryProgramId ? (
            <p className="md-muted">This member isn’t in a program yet, so they have no coach to message.</p>
          ) : (
            <>
              {thread.error && (
                <div className="banner banner-error" role="alert">
                  The conversation could not be loaded.
                </div>
              )}
              <ThreadPanel
                programId={primaryProgramId}
                memberUid={uid ?? ''}
                memberName={name}
                messages={thread.messages}
                myUid={user?.uid ?? ''}
                myName={user?.displayName ?? 'Your coach'}
                emptyHint={`${name} hasn’t written yet. You can start the conversation below.`}
              />
            </>
          )}
        </div>
      )}

      {tab === 'care' && (
        <div className="md-care">
          <div className="card md-records-card">
            <span className="overline">🩺 Consultations</span>
            {consults.error ? (
              <p className="md-muted">You don’t have access to consultations.</p>
            ) : consults.rows.length === 0 ? (
              <p className="md-muted">No consultation requests.</p>
            ) : (
              <table className="md-table">
                <tbody>
                  {consults.rows.map((c) => (
                    <tr key={String(c.id)}>
                      <td className="md-td-time">{formatDateTime(c.scheduledAt ?? c.createdAt)}</td>
                      <td>
                        {String(c.consultationType ?? 'Consultation')}
                        {c.concern ? ` — ${String(c.concern)}` : ''}
                      </td>
                      <td className="md-td-tags">
                        <span className="tag tag-neutral">{String(c.status ?? 'pending').replace('_', ' ')}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
          <div className="card md-records-card">
            <span className="overline">💬 Support requests</span>
            {support.error ? (
              <p className="md-muted">You don’t have access to support requests.</p>
            ) : support.rows.length === 0 ? (
              <p className="md-muted">No support requests.</p>
            ) : (
              <table className="md-table">
                <tbody>
                  {support.rows.map((r) => (
                    <tr key={String(r.id)}>
                      <td className="md-td-time">{formatDateTime(r.createdAt)}</td>
                      <td>
                        <strong>{String(r.subject ?? 'Support request')}</strong>
                        {r.message ? ` — ${String(r.message)}` : ''}
                      </td>
                      <td className="md-td-tags">
                        <span className="tag tag-neutral">{String(r.status ?? 'open')}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {tab === 'notes' && (
        <div className="md-notes">
          <div className="card md-note-composer">
            <span className="overline">Add a private note</span>
            {noteError && (
              <div className="banner banner-error" role="alert">
                {noteError}
              </div>
            )}
            <textarea
              className="textarea"
              placeholder="Consult prep, observations, follow-ups… members never see this."
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
            />
            <button className="btn btn-forest btn-sm" disabled={noteBusy || !noteText.trim()} onClick={() => void sendNote()}>
              {noteBusy ? 'Saving…' : 'Save note'}
            </button>
          </div>
          {notes.length === 0 ? (
            <div className="card empty">
              <div className="empty-mark" aria-hidden>🗒️</div>
              <p className="empty-title">No notes yet</p>
              <p className="empty-sub">Notes from any coach or admin about this member appear here.</p>
            </div>
          ) : (
            <div className="md-note-list">
              {notes.map((n) => (
                <div key={n.id} className="card md-note-card">
                  <p className="md-note-text">{n.text}</p>
                  <span className="md-note-meta">
                    {n.authorEmail ?? 'Staff'} · {relativeTime(n.createdAt)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}
