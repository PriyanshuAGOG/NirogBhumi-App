import { useEffect, useState } from 'react'
import {
  addDoc,
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { usePrograms, type Program } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { formatDate, toDate, toInputDateTime, fromInputDateTime } from '../lib/time'
import './Programs.css'

interface ProgramCode {
  id: string
  code?: string
  programId?: string
  active?: boolean
  maxUses?: number
  uses?: number
  expiresAt?: unknown
}

interface ProgramDraft {
  id: string | null
  name: string
  code: string
  coachName: string
  coachId: string
  startDate: string
  durationWeeks: string
}

interface CodeDraft {
  code: string
  programId: string
  maxUses: string
  expiresAt: string
}

function programDraftFrom(p: Program): ProgramDraft {
  return {
    id: p.id,
    name: p.name ?? '',
    code: p.code ?? '',
    coachName: p.coachName ?? '',
    coachId: p.coachId ?? '',
    startDate: toInputDateTime(p.startDate).slice(0, 10),
    durationWeeks: p.durationWeeks != null ? String(p.durationWeeks) : '',
  }
}

const emptyProgram: ProgramDraft = {
  id: null,
  name: '',
  code: '',
  coachName: '',
  coachId: '',
  startDate: '',
  durationWeeks: '',
}

export default function Programs() {
  const { programs, loading, error } = usePrograms()

  // Program editor
  const [pDraft, setPDraft] = useState<ProgramDraft | null>(null)
  const [pSaving, setPSaving] = useState(false)
  const [pError, setPError] = useState<string | null>(null)

  // Codes
  const [codes, setCodes] = useState<ProgramCode[]>([])
  const [codesError, setCodesError] = useState<string | null>(null)
  const [cDraft, setCDraft] = useState<CodeDraft | null>(null)
  const [cSaving, setCSaving] = useState(false)
  const [cError, setCError] = useState<string | null>(null)
  const [busyCode, setBusyCode] = useState<string | null>(null)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'programCodes')),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ProgramCode, 'id'>) }))
        next.sort((a, b) => (a.code ?? '').localeCompare(b.code ?? ''))
        setCodes(next)
        setCodesError(null)
      },
      (err) => setCodesError(errText(err, 'Could not load codes')),
    )
    return unsub
  }, [])

  const programName = (id?: string) => programs.find((p) => p.id === id)?.name ?? id ?? '—'

  async function saveProgram() {
    if (!pDraft || !pDraft.name.trim()) {
      setPError('A program name is required.')
      return
    }
    setPSaving(true)
    setPError(null)
    const start = fromInputDateTime(pDraft.startDate ? `${pDraft.startDate}T00:00` : '')
    const weeks = Number.parseInt(pDraft.durationWeeks, 10)
    const payload = {
      name: pDraft.name.trim(),
      code: pDraft.code.trim() || null,
      coachName: pDraft.coachName.trim() || null,
      coachId: pDraft.coachId.trim() || null,
      startDate: start ? Timestamp.fromDate(start) : null,
      durationWeeks: Number.isFinite(weeks) ? weeks : null,
      updatedAt: serverTimestamp(),
    }
    try {
      if (pDraft.id) {
        await setDoc(doc(db, 'programs', pDraft.id), payload, { merge: true })
      } else {
        await addDoc(collection(db, 'programs'), { ...payload, memberCount: 0, phases: [] })
      }
      setPDraft(null)
    } catch (err) {
      setPError(errText(err, 'Program could not be saved'))
    } finally {
      setPSaving(false)
    }
  }

  async function saveCode() {
    if (!cDraft || !cDraft.code.trim() || !cDraft.programId) {
      setCError('A code and a target program are required.')
      return
    }
    setCSaving(true)
    setCError(null)
    const max = Number.parseInt(cDraft.maxUses, 10)
    const exp = fromInputDateTime(cDraft.expiresAt ? `${cDraft.expiresAt}T23:59` : '')
    try {
      // Use the code text as the doc id so codes are unique & directly lookupable.
      await setDoc(doc(db, 'programCodes', cDraft.code.trim()), {
        code: cDraft.code.trim(),
        programId: cDraft.programId,
        active: true,
        maxUses: Number.isFinite(max) ? max : null,
        uses: 0,
        expiresAt: exp ? Timestamp.fromDate(exp) : null,
        createdAt: serverTimestamp(),
      })
      setCDraft(null)
    } catch (err) {
      setCError(errText(err, 'Code could not be created'))
    } finally {
      setCSaving(false)
    }
  }

  async function toggleCode(c: ProgramCode) {
    setBusyCode(c.id)
    setCodesError(null)
    try {
      await updateDoc(doc(db, 'programCodes', c.id), { active: !c.active })
    } catch (err) {
      setCodesError(errText(err, 'Could not update the code'))
    } finally {
      setBusyCode(null)
    }
  }

  function setP<K extends keyof ProgramDraft>(key: K, value: ProgramDraft[K]) {
    setPDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
  }
  function setC<K extends keyof CodeDraft>(key: K, value: CodeDraft[K]) {
    setCDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Programs &amp; codes</span>
        <h1>Programs &amp; codes</h1>
        <p className="page-lede">
          Create and edit batches, and manage the enrolment codes members use to join.
        </p>
      </header>

      {/* Programs */}
      <div className="toolbar">
        <h2 className="section-h">Programs</h2>
        <div className="toolbar-spacer" />
        <button
          className="btn btn-forest"
          onClick={() => {
            setPDraft({ ...emptyProgram })
            setPError(null)
          }}
        >
          + New program
        </button>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading programs…</p>
        </div>
      ) : programs.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🌱</div>
          <p className="empty-title">No programs yet</p>
          <p className="empty-sub">Create your first batch to get started.</p>
        </div>
      ) : (
        <div className="prog-list">
          {programs.map((p) => (
            <div key={p.id} className="card prog-row">
              <div className="prog-main">
                <span className="prog-name">{p.name ?? 'Untitled program'}</span>
                <span className="prog-sub">
                  {p.coachName ? `Coach ${p.coachName}` : 'No coach'}
                  {p.startDate ? ` · starts ${formatDate(p.startDate)}` : ''}
                  {p.durationWeeks ? ` · ${p.durationWeeks} weeks` : ''}
                </span>
              </div>
              <div className="prog-meta">
                {p.code && <span className="tag tag-warn">{p.code}</span>}
                <span className="prog-members">{p.memberCount ?? 0} members</span>
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => {
                    setPDraft(programDraftFrom(p))
                    setPError(null)
                  }}
                >
                  Edit
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Codes */}
      <div className="toolbar">
        <h2 className="section-h">Program codes</h2>
        <div className="toolbar-spacer" />
        <button
          className="btn btn-forest"
          disabled={programs.length === 0}
          onClick={() => {
            setCDraft({
              code: '',
              programId: programs[0]?.id ?? '',
              maxUses: '',
              expiresAt: '',
            })
            setCError(null)
          }}
        >
          + New code
        </button>
      </div>

      {codesError && (
        <div className="banner banner-error" role="alert">
          {codesError}
        </div>
      )}

      {codes.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🔑</div>
          <p className="empty-title">No codes yet</p>
          <p className="empty-sub">Create a code so members can join a batch.</p>
        </div>
      ) : (
        <div className="code-list">
          {codes.map((c) => {
            const expired = (() => {
              const d = toDate(c.expiresAt)
              return d ? d.getTime() < Date.now() : false
            })()
            return (
              <div key={c.id} className="card code-row">
                <div className="code-main">
                  <span className="code-text">{c.code ?? c.id}</span>
                  <span className="code-sub">
                    {programName(c.programId)}
                    {' · '}
                    {c.uses ?? 0}
                    {c.maxUses ? ` / ${c.maxUses}` : ''} used
                    {toDate(c.expiresAt) ? ` · expires ${formatDate(c.expiresAt)}` : ''}
                  </span>
                </div>
                <div className="code-meta">
                  {expired ? (
                    <span className="tag tag-bad">Expired</span>
                  ) : c.active ? (
                    <span className="tag tag-good">Active</span>
                  ) : (
                    <span className="tag tag-neutral">Off</span>
                  )}
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={busyCode === c.id}
                    onClick={() => void toggleCode(c)}
                  >
                    {c.active ? 'Deactivate' : 'Activate'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Program editor modal */}
      {pDraft && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setPDraft(null)}>
          <div className="modal card prog-modal" onClick={(e) => e.stopPropagation()}>
            <span className="overline">{pDraft.id ? 'Edit program' : 'New program'}</span>
            <h2 className="modal-title">{pDraft.id ? 'Edit program' : 'Create a program'}</h2>
            {pError && (
              <div className="banner banner-error" role="alert">
                {pError}
              </div>
            )}
            <div className="field">
              <span className="field-label">Name</span>
              <input className="input" value={pDraft.name} onChange={(e) => setP('name', e.target.value)} placeholder="July 2026 Batch" />
            </div>
            <div className="field-row">
              <div className="field">
                <span className="field-label">Code label (optional)</span>
                <input className="input" value={pDraft.code} onChange={(e) => setP('code', e.target.value)} placeholder="JULY26" />
              </div>
              <div className="field">
                <span className="field-label">Duration (weeks)</span>
                <input className="input" type="number" min="0" value={pDraft.durationWeeks} onChange={(e) => setP('durationWeeks', e.target.value)} />
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <span className="field-label">Coach name</span>
                <input className="input" value={pDraft.coachName} onChange={(e) => setP('coachName', e.target.value)} placeholder="Dr. Meera" />
              </div>
              <div className="field">
                <span className="field-label">Coach UID (optional)</span>
                <input className="input" value={pDraft.coachId} onChange={(e) => setP('coachId', e.target.value)} />
              </div>
            </div>
            <div className="field">
              <span className="field-label">Start date</span>
              <input className="input" type="date" value={pDraft.startDate} onChange={(e) => setP('startDate', e.target.value)} />
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setPDraft(null)}>
                Cancel
              </button>
              <button className="btn btn-forest" disabled={pSaving} onClick={() => void saveProgram()}>
                {pSaving ? 'Saving…' : pDraft.id ? 'Save changes' : 'Create program'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Code editor modal */}
      {cDraft && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setCDraft(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <span className="overline">New code</span>
            <h2 className="modal-title">Create a program code</h2>
            {cError && (
              <div className="banner banner-error" role="alert">
                {cError}
              </div>
            )}
            <div className="field">
              <span className="field-label">Code</span>
              <input className="input" value={cDraft.code} onChange={(e) => setC('code', e.target.value)} placeholder="JULY26" />
            </div>
            <div className="field">
              <span className="field-label">Program</span>
              <select className="select" value={cDraft.programId} onChange={(e) => setC('programId', e.target.value)}>
                {programs.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name ?? p.id}
                  </option>
                ))}
              </select>
            </div>
            <div className="field-row">
              <div className="field">
                <span className="field-label">Max uses (optional)</span>
                <input className="input" type="number" min="0" value={cDraft.maxUses} onChange={(e) => setC('maxUses', e.target.value)} />
              </div>
              <div className="field">
                <span className="field-label">Expires (optional)</span>
                <input className="input" type="date" value={cDraft.expiresAt} onChange={(e) => setC('expiresAt', e.target.value)} />
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setCDraft(null)}>
                Cancel
              </button>
              <button className="btn btn-forest" disabled={cSaving} onClick={() => void saveCode()}>
                {cSaving ? 'Creating…' : 'Create code'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
