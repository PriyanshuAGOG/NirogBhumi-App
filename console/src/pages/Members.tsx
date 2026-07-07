import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, limit, onSnapshot, orderBy, query, where } from 'firebase/firestore'
import { httpsCallable } from 'firebase/functions'
import { db, functions } from '../lib/firebase'
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { consistencyTag } from '../lib/health'
import { toCsv, downloadCsv } from '../lib/csv'
import './Members.css'

// Every user account, not just people currently enrolled in a program - a
// roster keyed off `programMembers` (the previous approach) structurally
// excludes anyone without an active program, and staff repeatedly asked to
// see everyone through one screen instead of switching between "enrolled"
// and "everyone else" views. MemberDetail already reads every log
// collection straight off the uid regardless of enrollment, so the only
// change needed here is the roster's own data source.
interface RosterEntry {
  id: string
  name?: string
  email?: string
  phone?: string
  programActive?: boolean
  activeProgramId?: string
  activeProgramName?: string
  lastCheckinAt?: unknown
}

const NOT_ENROLLED = '__not_enrolled__'

function downloadRosterCsv(rows: RosterEntry[], programName: (id?: string) => string) {
  const header = ['Name', 'Email', 'Phone', 'Program', 'Status', 'Last check-in']
  const lines = [header]
  rows.forEach((m) => {
    const c = consistencyTag(m.lastCheckinAt)
    lines.push([
      m.name ?? m.id,
      m.email ?? '',
      m.phone ?? '',
      m.programActive ? programName(m.activeProgramId) : 'Not enrolled',
      m.programActive ? c.tag : '—',
      m.programActive ? c.text : '—',
    ])
  })
  downloadCsv(`roster_${new Date().toISOString().slice(0, 10)}.csv`, toCsv(lines))
}

export default function Members() {
  const { programs } = usePrograms()
  const [members, setMembers] = useState<RosterEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [programFilter, setProgramFilter] = useState<string>('all')
  const [onlyQuiet, setOnlyQuiet] = useState(false)
  const [composerOpen, setComposerOpen] = useState(false)
  const [bulkTitle, setBulkTitle] = useState('')
  const [bulkBody, setBulkBody] = useState('')
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)
  const [sendResult, setSendResult] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    // Scoped to one program when a batch is picked (mirrors Batches.tsx's
    // own per-program listener); "all" and "not enrolled" are bounded by
    // limit() the same way Users.tsx bounds its own full-roster listener,
    // since `users` is the platform's hottest-write collection.
    const constraints =
      programFilter === 'all' || programFilter === NOT_ENROLLED
        ? [orderBy('createdAt', 'desc'), limit(1000)]
        : [where('activeProgramId', '==', programFilter)]
    const unsub = onSnapshot(
      query(collection(db, 'users'), ...constraints),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<RosterEntry, 'id'>) }))
        next.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
        setMembers(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load members'))
      },
    )
    return unsub
  }, [programFilter])

  const programName = useMemo(() => {
    const map = new Map(programs.map((p) => [p.id, p.name ?? 'Program']))
    return (id?: string) => (id ? map.get(id) ?? 'Program' : '—')
  }, [programs])

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase()
    return members.filter((m) => {
      if (programFilter === NOT_ENROLLED && m.programActive) return false
      if (programFilter !== 'all' && programFilter !== NOT_ENROLLED && m.activeProgramId !== programFilter) return false
      if (q && !(m.name ?? m.email ?? m.phone ?? m.id).toLowerCase().includes(q)) return false
      if (onlyQuiet && (!m.programActive || consistencyTag(m.lastCheckinAt).cls !== 'tag-bad')) return false
      return true
    })
  }, [members, search, programFilter, onlyQuiet])

  // Bulk messaging is scoped to one program at a time - the callable itself
  // enforces this boundary (programStaff(programId)) but requiring a single
  // program here too means the button's own quiet-member count is honest,
  // not a mix of members across batches this coach may not all manage.
  const quietInSelectedProgram = useMemo(
    () =>
      programFilter === 'all' || programFilter === NOT_ENROLLED
        ? []
        : members.filter((m) => m.activeProgramId === programFilter && consistencyTag(m.lastCheckinAt).cls === 'tag-bad'),
    [members, programFilter],
  )

  async function sendBulkMessage() {
    if (programFilter === 'all' || programFilter === NOT_ENROLLED || !bulkTitle.trim() || !bulkBody.trim() || quietInSelectedProgram.length === 0) return
    setSending(true)
    setSendError(null)
    setSendResult(null)
    try {
      const sendBulkNotification = httpsCallable<
        { programId: string; title: string; body: string; uids: string[] },
        { sent: number }
      >(functions, 'sendBulkNotification')
      const res = await sendBulkNotification({
        programId: programFilter,
        title: bulkTitle.trim(),
        body: bulkBody.trim(),
        uids: quietInSelectedProgram.map((m) => m.id),
      })
      setSendResult(`Sent to ${res.data.sent} quiet member${res.data.sent === 1 ? '' : 's'}.`)
      setBulkTitle('')
      setBulkBody('')
    } catch (err) {
      setSendError(errText(err, 'Could not send the message'))
    } finally {
      setSending(false)
    }
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Members</span>
        <h1>All members</h1>
        <p className="page-lede">
          Everyone on the platform, enrolled or not. Open a member to see their full history -
          every reading, log, and report they've ever recorded.
        </p>
      </header>

      <div className="toolbar">
        <input
          className="input mem-search"
          placeholder="Search by name, email or phone…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          className="select mem-program-select"
          value={programFilter}
          onChange={(e) => setProgramFilter(e.target.value)}
        >
          <option value="all">Everyone</option>
          <option value={NOT_ENROLLED}>Not enrolled</option>
          {programs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name ?? 'Program'}
            </option>
          ))}
        </select>
        <label className="check">
          <input type="checkbox" checked={onlyQuiet} onChange={(e) => setOnlyQuiet(e.target.checked)} />
          Needs attention only
        </label>
        <button className="btn btn-ghost" onClick={() => downloadRosterCsv(visible, programName)} disabled={visible.length === 0}>
          Export roster (CSV)
        </button>
        <button
          className="btn btn-ghost"
          disabled={programFilter === 'all' || programFilter === NOT_ENROLLED || quietInSelectedProgram.length === 0}
          onClick={() => setComposerOpen((v) => !v)}
          title={programFilter === 'all' || programFilter === NOT_ENROLLED ? 'Pick a single program to message its quiet members' : undefined}
        >
          Message quiet members ({programFilter === 'all' || programFilter === NOT_ENROLLED ? 0 : quietInSelectedProgram.length})
        </button>
      </div>

      {composerOpen && programFilter !== 'all' && programFilter !== NOT_ENROLLED && (
        <div className="card composer">
          <span className="overline">
            Message all {quietInSelectedProgram.length} quiet member
            {quietInSelectedProgram.length === 1 ? '' : 's'} in {programName(programFilter)}
          </span>
          {sendError && (
            <div className="banner banner-error" role="alert">
              {sendError}
            </div>
          )}
          {sendResult && (
            <div className="banner banner-success" role="status">
              {sendResult}
            </div>
          )}
          <div className="field">
            <span className="field-label">Title</span>
            <input
              className="input"
              value={bulkTitle}
              onChange={(e) => setBulkTitle(e.target.value)}
              placeholder="We miss seeing your check-ins"
            />
          </div>
          <div className="field">
            <span className="field-label">Message</span>
            <textarea
              className="textarea"
              value={bulkBody}
              onChange={(e) => setBulkBody(e.target.value)}
              placeholder="A short, warm nudge - not a scolding."
            />
          </div>
          <div className="composer-actions">
            <button
              className="btn btn-forest"
              disabled={sending || !bulkTitle.trim() || !bulkBody.trim()}
              onClick={() => void sendBulkMessage()}
            >
              {sending ? 'Sending…' : `Send to ${quietInSelectedProgram.length} member${quietInSelectedProgram.length === 1 ? '' : 's'}`}
            </button>
          </div>
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
          <p>Loading members…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>💚</div>
          <p className="empty-title">No members found</p>
          <p className="empty-sub">
            {search || programFilter !== 'all' || onlyQuiet
              ? 'Try a different search or filter.'
              : 'As people sign up, they will appear here.'}
          </p>
        </div>
      ) : (
        <div className="mem-list">
          {visible.map((m) => {
            const c = consistencyTag(m.lastCheckinAt)
            return (
              <Link key={m.id} to={`/members/${m.id}`} className="card mem-row">
                <div className="mem-main">
                  <span className="mem-name">{m.name ?? m.email ?? m.phone ?? 'Member'}</span>
                  <span className="mem-sub">
                    {m.programActive ? `${programName(m.activeProgramId)} · ${c.text}` : 'Not enrolled'}
                  </span>
                </div>
                {m.programActive ? (
                  <span className={`tag ${c.cls}`}>{c.tag}</span>
                ) : (
                  <span className="tag tag-neutral">Not enrolled</span>
                )}
              </Link>
            )
          })}
        </div>
      )}
    </section>
  )
}
