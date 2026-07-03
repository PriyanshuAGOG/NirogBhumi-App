import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { collection, onSnapshot, query } from 'firebase/firestore'
import { db } from '../lib/firebase'
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

export default function Members() {
  const { programs } = usePrograms()
  const [members, setMembers] = useState<RosterEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [programFilter, setProgramFilter] = useState<string>('all')
  const [onlyQuiet, setOnlyQuiet] = useState(false)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'programMembers')),
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
  }, [])

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
      </div>

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
