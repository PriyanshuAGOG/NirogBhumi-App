import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, limit, onSnapshot, query, where } from 'firebase/firestore'
import { httpsCallable } from 'firebase/functions'
import { db, functions } from '../lib/firebase'
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { consistencyTag } from '../lib/health'
import './Members.css'

interface RosterEntry {
  id: string
  programId?: string
  uid?: string
  name?: string
  lastCheckinAt?: unknown
}

function csvCell(value: string): string {
  // Quote whenever the cell could otherwise be misread (comma/quote/newline),
  // and double any embedded quotes - the standard CSV escaping rule, not
  // just enough to look right in a spreadsheet preview.
  if (/[",\n]/.test(value)) return `"${value.replace(/"/g, '""')}"`
  return value
}

function downloadRosterCsv(rows: RosterEntry[], programName: (id?: string) => string) {
  const header = ['Name', 'Program', 'Status', 'Last check-in']
  const lines = [header.map(csvCell).join(',')]
  rows.forEach((m) => {
    const c = consistencyTag(m.lastCheckinAt)
    lines.push(
      [m.name ?? m.uid ?? 'Member', programName(m.programId), c.tag, c.text].map(csvCell).join(','),
    )
  })
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `roster_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
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
    // Scoped to one program's roster when a batch is picked - matches
    // Batches.tsx's own per-program listener. The "all programs" view is
    // capped instead of unbounded: programMembers docs get touched on every
    // check-in platform-wide now that lastCheckinAt is mirrored onto them
    // (see the onUserCheckinMirror function), so an unfiltered listener here
    // would re-render this page on every single check-in from every member.
    const constraints =
      programFilter === 'all' ? [limit(1000)] : [where('programId', '==', programFilter)]
    const unsub = onSnapshot(
      query(collection(db, 'programMembers'), ...constraints),
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
      if (programFilter !== 'all' && m.programId !== programFilter) return false
      if (q && !(m.name ?? m.uid ?? '').toLowerCase().includes(q)) return false
      if (onlyQuiet && consistencyTag(m.lastCheckinAt).cls !== 'tag-bad') return false
      return true
    })
  }, [members, search, programFilter, onlyQuiet])

  // Bulk messaging is scoped to one program at a time - the callable itself
  // enforces this boundary (programStaff(programId)) but requiring a single
  // program here too means the button's own quiet-member count is honest,
  // not a mix of members across batches this coach may not all manage.
  const quietInSelectedProgram = useMemo(
    () =>
      programFilter === 'all'
        ? []
        : members.filter((m) => m.programId === programFilter && consistencyTag(m.lastCheckinAt).cls === 'tag-bad'),
    [members, programFilter],
  )

  async function sendBulkMessage() {
    if (programFilter === 'all' || !bulkTitle.trim() || !bulkBody.trim() || quietInSelectedProgram.length === 0) return
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
        uids: quietInSelectedProgram.map((m) => m.uid ?? '').filter(Boolean),
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
        <h1>Care+ member monitoring</h1>
        <p className="page-lede">
          Every person enrolled in a program, across every batch. Open a member to see their
          day-to-day logs and prepare for a consult.
        </p>
      </header>

      <div className="toolbar">
        <input
          className="input mem-search"
          placeholder="Search by name…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          className="select mem-program-select"
          value={programFilter}
          onChange={(e) => setProgramFilter(e.target.value)}
        >
          <option value="all">All programs</option>
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
          disabled={programFilter === 'all' || quietInSelectedProgram.length === 0}
          onClick={() => setComposerOpen((v) => !v)}
          title={programFilter === 'all' ? 'Pick a single program to message its quiet members' : undefined}
        >
          Message quiet members ({programFilter === 'all' ? 0 : quietInSelectedProgram.length})
        </button>
      </div>

      {composerOpen && programFilter !== 'all' && (
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
              : 'As people redeem a program code, they will appear here.'}
          </p>
        </div>
      ) : (
        <div className="mem-list">
          {visible.map((m) => {
            const c = consistencyTag(m.lastCheckinAt)
            return (
              <Link key={m.id} to={`/members/${m.uid ?? m.id}`} className="card mem-row">
                <div className="mem-main">
                  <span className="mem-name">{m.name ?? m.uid ?? 'Member'}</span>
                  <span className="mem-sub">
                    {programName(m.programId)} · {c.text}
                  </span>
                </div>
                <span className={`tag ${c.cls}`}>{c.tag}</span>
              </Link>
            )
          })}
        </div>
      )}
    </section>
  )
}
