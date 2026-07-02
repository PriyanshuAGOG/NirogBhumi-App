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
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { relativeTime, formatDateTime } from '../lib/time'
import { consistencyTag, isAlertLog, LOG_COLLECTIONS, summarizeLog, type LogEntry, type LogKind } from '../lib/health'
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
function useLogFeed(kind: LogKind, uid: string | undefined): { entries: LogEntry[]; error: boolean } {
  const [entries, setEntries] = useState<LogEntry[]>([])
  const [error, setError] = useState(false)

  useEffect(() => {
    if (!uid) return
    const unsub = onSnapshot(
      query(collection(db, kind), where('userId', '==', uid), orderBy('createdAt', 'desc'), limit(20)),
      (snap) => {
        setEntries(snap.docs.map((d) => ({ id: d.id, kind, at: d.data().createdAt, data: d.data() })))
        setError(false)
      },
      () => setError(true),
    )
    return unsub
    // kind is a stable literal per call site; only uid changes across renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid])

  return { entries, error }
}

export default function MemberDetail() {
  const { uid } = useParams<{ uid: string }>()
  const { user } = useAuth()
  const { programs } = usePrograms()
  const [profile, setProfile] = useState<MemberProfile | null>(null)
  const [profileLoading, setProfileLoading] = useState(true)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [roster, setRoster] = useState<RosterEntry[]>([])
  const [notes, setNotes] = useState<CoachNote[]>([])
  const [noteText, setNoteText] = useState('')
  const [noteBusy, setNoteBusy] = useState(false)
  const [noteError, setNoteError] = useState<string | null>(null)
  const [tab, setTab] = useState<'overview' | 'logs' | 'notes'>('overview')
  const [logFilter, setLogFilter] = useState<LogKind | 'all'>('all')

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
    const unsub = onSnapshot(query(collection(db, 'programMembers'), where('uid', '==', uid)), (snap) => {
      setRoster(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<RosterEntry, 'id'>) })))
    })
    return unsub
  }, [uid])

  useEffect(() => {
    if (!uid) return
    const unsub = onSnapshot(
      query(collection(db, 'coachNotes'), where('targetUid', '==', uid), orderBy('createdAt', 'desc')),
      (snap) => setNotes(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<CoachNote, 'id'>) }))),
    )
    return unsub
  }, [uid])

  const glucose = useLogFeed('glucoseReadings', uid)
  const bp = useLogFeed('bpReadings', uid)
  const weight = useLogFeed('weightLogs', uid)
  const sleep = useLogFeed('sleepLogs', uid)
  const walk = useLogFeed('walkLogs', uid)
  const labs = useLogFeed('labReports', uid)
  const checklist = useLogFeed('checklistLogs', uid)
  const checkins = useLogFeed('dailyCheckins', uid)
  const feeds = useMemo(
    () => ({ glucoseReadings: glucose, bpReadings: bp, weightLogs: weight, sleepLogs: sleep, walkLogs: walk, labReports: labs, checklistLogs: checklist, dailyCheckins: checkins }),
    [glucose, bp, weight, sleep, walk, labs, checklist, checkins],
  )

  const timeline = useMemo(() => {
    const all = Object.values(feeds).flatMap((f) => f.entries)
    all.sort((a, b) => {
      const ta = (a.at as { seconds?: number })?.seconds ?? 0
      const tb = (b.at as { seconds?: number })?.seconds ?? 0
      return tb - ta
    })
    return logFilter === 'all' ? all : all.filter((e) => e.kind === logFilter)
  }, [feeds, logFilter])

  const lastActivity = timeline[0]?.at
  const alerts = useMemo(() => timeline.filter(isAlertLog).slice(0, 5), [timeline])

  const primaryProgramId = roster[0]?.programId ?? profile?.activeProgramId
  const programName = programs.find((p) => p.id === primaryProgramId)?.name ?? profile?.activeProgramName

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

      <div className="seg" role="tablist">
        {(['overview', 'logs', 'notes'] as const).map((t) => (
          <button
            key={t}
            className={'seg-btn' + (tab === t ? ' seg-btn-active' : '')}
            onClick={() => setTab(t)}
          >
            {t === 'overview' ? 'Overview' : t === 'logs' ? 'Day-to-day logs' : `Notes (${notes.length})`}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div className="md-overview-grid">
          <div className="card md-stat-card">
            <span className="tile-label">Fasting sugar</span>
            <span className="md-stat-value">
              {profile.latestMetrics?.fastingSugar != null ? `${profile.latestMetrics.fastingSugar} mg/dL` : '—'}
            </span>
            <span className="tile-hint">
              {profile.latestMetrics?.glucoseUpdatedAt ? relativeTime(profile.latestMetrics.glucoseUpdatedAt) : 'No readings yet'}
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
            <span className="tile-label">Entries logged</span>
            <span className="md-stat-value">{timeline.length}</span>
            <span className="tile-hint">Across the last 20 per category</span>
          </div>
        </div>
      )}

      {tab === 'logs' && (
        <div className="md-logs">
          <div className="toolbar">
            <select className="select" value={logFilter} onChange={(e) => setLogFilter(e.target.value as LogKind | 'all')}>
              <option value="all">All categories</option>
              {LOG_COLLECTIONS.map((l) => (
                <option key={l.kind} value={l.kind}>
                  {l.icon} {l.label}
                </option>
              ))}
            </select>
          </div>
          {timeline.length === 0 ? (
            <div className="card empty">
              <div className="empty-mark" aria-hidden>📋</div>
              <p className="empty-title">No logs yet</p>
              <p className="empty-sub">Entries this member records will show up here in real time.</p>
            </div>
          ) : (
            <div className="md-timeline">
              {timeline.map((entry) => (
                <div key={`${entry.kind}-${entry.id}`} className="card md-log-row">
                  <span className="md-log-icon" aria-hidden>
                    {LOG_COLLECTIONS.find((l) => l.kind === entry.kind)?.icon}
                  </span>
                  <div className="md-log-main">
                    <span className="md-log-label">{LOG_COLLECTIONS.find((l) => l.kind === entry.kind)?.label}</span>
                    <span className="md-log-summary">{summarizeLog(entry)}</span>
                  </div>
                  <span className="md-log-time">{relativeTime(entry.at)}</span>
                </div>
              ))}
            </div>
          )}
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
