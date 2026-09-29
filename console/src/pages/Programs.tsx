import { useEffect, useRef, useState } from 'react'
import {
  addDoc,
  collection,
  doc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore'
import { httpsCallable } from 'firebase/functions'
import { FirebaseError } from 'firebase/app'
import { db, functions } from '../lib/firebase'
import { chunk, type Program } from '../lib/usePrograms'
import { useScope } from '../lib/scope'
import { errText } from '../lib/errors'
import { formatDate, toDate, toInputDateTime, fromInputDateTime } from '../lib/time'
import { parseCsvRecords, toCsv, downloadCsv } from '../lib/csv'
import { callBulkOnboard, summarizeBulkResults } from '../lib/bulkOnboard'
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

interface ProgramInvite {
  id: string
  contact?: string
  contactType?: 'email' | 'phone'
  programId?: string
  programName?: string
  createdAt?: unknown
  consumedAt?: unknown
}

interface InviteDraft {
  contact: string
  programId: string
}

const NOT_DEPLOYED = new Set(['functions/not-found', 'functions/unavailable', 'functions/internal'])

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
  const { programs, programIds, programKey, isAdmin, loading, error } = useScope()

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
  const [copiedCode, setCopiedCode] = useState<string | null>(null)

  // Invites - pre-enroll by phone/email, consumed automatically at signup
  const [invites, setInvites] = useState<ProgramInvite[]>([])
  const [invitesError, setInvitesError] = useState<string | null>(null)
  const [iDraft, setIDraft] = useState<InviteDraft | null>(null)
  const [iSaving, setISaving] = useState(false)
  const [iError, setIError] = useState<string | null>(null)
  const [busyInvite, setBusyInvite] = useState<string | null>(null)
  const csvInputRef = useRef<HTMLInputElement>(null)
  const [csvBusy, setCsvBusy] = useState(false)
  const [csvError, setCsvError] = useState<string | null>(null)
  const [csvSummary, setCsvSummary] = useState<string | null>(null)

  // Invites and codes are tied to a program. An admin reads them all; a coach
  // must ask only for the programs they coach (Firestore rejects a list query it
  // can't prove is limited to those), so those are read per program (chunked
  // `in`) and ordered here.
  useEffect(() => {
    if (!isAdmin && (loading || programIds.length === 0)) {
      setInvites([])
      return
    }
    const createdMillis = (i: ProgramInvite) => (i.createdAt as { toMillis?: () => number } | undefined)?.toMillis?.() ?? 0
    const queries = isAdmin
      ? [query(collection(db, 'programInvites'), orderBy('createdAt', 'desc'))]
      : chunk(programIds).map((ids) => query(collection(db, 'programInvites'), where('programId', 'in', ids)))
    const perChunk = new Map<number, ProgramInvite[]>()
    const unsubs = queries.map((q, index) =>
      onSnapshot(
        q,
        (snap) => {
          perChunk.set(index, snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ProgramInvite, 'id'>) })))
          setInvites(Array.from(perChunk.values()).flat().sort((a, b) => createdMillis(b) - createdMillis(a)))
          setInvitesError(null)
        },
        (err) => setInvitesError(errText(err, 'Could not load invites')),
      ),
    )
    return () => unsubs.forEach((u) => u())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, programKey, loading])

  useEffect(() => {
    if (!isAdmin && (loading || programIds.length === 0)) {
      setCodes([])
      return
    }
    const queries = isAdmin
      ? [query(collection(db, 'programCodes'))]
      : chunk(programIds).map((ids) => query(collection(db, 'programCodes'), where('programId', 'in', ids)))
    const perChunk = new Map<number, ProgramCode[]>()
    const unsubs = queries.map((q, index) =>
      onSnapshot(
        q,
        (snap) => {
          perChunk.set(index, snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ProgramCode, 'id'>) })))
          setCodes(Array.from(perChunk.values()).flat().sort((a, b) => (a.code ?? '').localeCompare(b.code ?? '')))
          setCodesError(null)
        },
        (err) => setCodesError(errText(err, 'Could not load codes')),
      ),
    )
    return () => unsubs.forEach((u) => u())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, programKey, loading])

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
    if (!cDraft) return
    // Codes are matched case-insensitively by the redeem function, which looks
    // the code up by its upper-cased text - so it is stored that way here.
    const code = cDraft.code.trim().toUpperCase()
    if (!code || !cDraft.programId) {
      setCError('A code and a target program are required.')
      return
    }
    if (!/^[A-Z0-9_-]{4,24}$/.test(code)) {
      setCError('Use 4–24 letters, numbers, dashes or underscores (no spaces).')
      return
    }
    if (codes.some((c) => (c.code ?? c.id).toUpperCase() === code)) {
      setCError('That code already exists. Pick a different one, or reactivate the existing code.')
      return
    }
    setCSaving(true)
    setCError(null)
    const max = Number.parseInt(cDraft.maxUses, 10)
    const exp = fromInputDateTime(cDraft.expiresAt ? `${cDraft.expiresAt}T23:59` : '')
    try {
      // Use the code text as the doc id so codes are unique & directly lookupable.
      await setDoc(doc(db, 'programCodes', code), {
        code,
        programId: cDraft.programId,
        active: true,
        maxUses: Number.isFinite(max) && max > 0 ? max : null,
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

  // Unambiguous alphabet (no 0/O/1/I) so a code read out over a call or typed
  // from a screenshot doesn't fail on a look-alike character.
  function generateCode(): string {
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    const bytes = crypto.getRandomValues(new Uint8Array(8))
    return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join('')
  }

  async function copyCode(c: ProgramCode) {
    try {
      await navigator.clipboard.writeText(c.code ?? c.id)
      setCopiedCode(c.id)
      window.setTimeout(() => setCopiedCode((cur) => (cur === c.id ? null : cur)), 1800)
    } catch {
      setCodesError('Could not copy - select the code and copy it manually.')
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

  async function saveInvite() {
    if (!iDraft || !iDraft.contact.trim() || !iDraft.programId) {
      setIError('A phone number or email and a target program are required.')
      return
    }
    setISaving(true)
    setIError(null)
    try {
      const inviteToProgram = httpsCallable<{ contact: string; programId: string }, { contact: string }>(
        functions,
        'inviteToProgram',
      )
      await inviteToProgram({ contact: iDraft.contact.trim(), programId: iDraft.programId })
      setIDraft(null)
    } catch (err) {
      const notDeployed = err instanceof FirebaseError && NOT_DEPLOYED.has(err.code)
      setIError(notDeployed ? 'Invite service not deployed yet.' : errText(err, 'Could not create invite'))
    } finally {
      setISaving(false)
    }
  }

  async function removeInvite(inv: ProgramInvite) {
    setBusyInvite(inv.id)
    setInvitesError(null)
    try {
      const revokeInvite = httpsCallable<{ id: string }, unknown>(functions, 'revokeInvite')
      await revokeInvite({ id: inv.id })
    } catch (err) {
      setInvitesError(errText(err, 'Could not revoke the invite'))
    } finally {
      setBusyInvite(null)
    }
  }

  function downloadInviteTemplate() {
    const header = ['contact', 'program']
    const example = ['+919876543210', programs[0]?.name ?? 'July 2026 Batch']
    downloadCsv('onboard_by_contact_template.csv', toCsv([header, example]))
  }

  async function handleInviteCsv(file: File) {
    setCsvBusy(true)
    setCsvError(null)
    setCsvSummary(null)
    try {
      const text = await file.text()
      const records = parseCsvRecords(text)
      if (records.length === 0) { setCsvError('That file has no data rows.'); return }
      if (records.length > 300) { setCsvError('Too many rows in one file (max 300) - split it into smaller batches.'); return }

      const programByName = new Map(programs.map((p) => [(p.name ?? '').trim().toLowerCase(), p.id]))
      const rows: { contact: string; programId: string }[] = []
      const rowErrors: string[] = []
      records.forEach((rec, idx) => {
        const rowNum = idx + 2
        const contact = (rec.contact ?? '').trim()
        const programName = (rec.program ?? '').trim()
        if (!contact) { rowErrors.push(`Row ${rowNum}: missing contact`); return }
        const programId = programByName.get(programName.toLowerCase())
        if (!programId) { rowErrors.push(`Row ${rowNum}: program "${programName}" doesn't match any existing program`); return }
        rows.push({ contact, programId })
      })
      if (rows.length === 0) { setCsvError(`No rows could be imported.\n${rowErrors.join('\n')}`); return }

      const results = await callBulkOnboard(rows)
      setCsvSummary(summarizeBulkResults(results) + (rowErrors.length ? ` Also skipped ${rowErrors.length} row(s) before import: ${rowErrors.join('; ')}` : ''))
    } catch (err) {
      const notDeployed = err instanceof FirebaseError && NOT_DEPLOYED.has(err.code)
      setCsvError(notDeployed ? 'Bulk import service not deployed yet.' : errText(err, 'Could not import that file'))
    } finally {
      setCsvBusy(false)
      if (csvInputRef.current) csvInputRef.current.value = ''
    }
  }

  function setP<K extends keyof ProgramDraft>(key: K, value: ProgramDraft[K]) {
    setPDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
  }
  function setC<K extends keyof CodeDraft>(key: K, value: CodeDraft[K]) {
    setCDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
  }
  function setI<K extends keyof InviteDraft>(key: K, value: InviteDraft[K]) {
    setIDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
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
        {isAdmin && (
          <button
            className="btn btn-forest"
            onClick={() => {
              setPDraft({ ...emptyProgram })
              setPError(null)
            }}
          >
            + New program
          </button>
        )}
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
                  <button className="btn btn-ghost btn-sm" onClick={() => void copyCode(c)}>
                    {copiedCode === c.id ? 'Copied ✓' : 'Copy'}
                  </button>
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

      {/* Invites - pre-enroll by phone/email */}
      <div className="toolbar">
        <h2 className="section-h">Onboard by phone or email</h2>
        <div className="toolbar-spacer" />
        <button className="btn btn-ghost" disabled={programs.length === 0} onClick={downloadInviteTemplate}>
          Download CSV template
        </button>
        <button
          className="btn btn-ghost"
          disabled={programs.length === 0 || csvBusy}
          onClick={() => csvInputRef.current?.click()}
        >
          {csvBusy ? 'Importing…' : 'Upload CSV'}
        </button>
        <input
          ref={csvInputRef}
          type="file"
          accept=".csv,text/csv"
          style={{ display: 'none' }}
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) void handleInviteCsv(file)
          }}
        />
        <button
          className="btn btn-forest"
          disabled={programs.length === 0}
          onClick={() => {
            setIDraft({ contact: '', programId: programs[0]?.id ?? '' })
            setIError(null)
          }}
        >
          + New invite
        </button>
      </div>
      <p className="page-lede">
        Add someone's phone number or email here before they've signed up - the moment they create
        an account with that exact contact, they're enrolled automatically. No code to hand out.
        Upload a CSV to onboard many at once - anyone who already has an account is enrolled
        immediately instead of waiting for signup.
      </p>

      {csvError && (
        <div className="banner banner-error" role="alert" style={{ whiteSpace: 'pre-line' }}>
          {csvError}
        </div>
      )}
      {csvSummary && (
        <div className="banner banner-success" role="status">
          {csvSummary}
        </div>
      )}

      {invitesError && (
        <div className="banner banner-error" role="alert">
          {invitesError}
        </div>
      )}

      {invites.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>📇</div>
          <p className="empty-title">No invites yet</p>
          <p className="empty-sub">Add a phone number or email to pre-enroll someone.</p>
        </div>
      ) : (
        <div className="code-list">
          {invites.map((inv) => (
            <div key={inv.id} className="card code-row">
              <div className="code-main">
                <span className="code-text">{inv.contact ?? inv.id}</span>
                <span className="code-sub">
                  {programName(inv.programId)}
                  {inv.contactType ? ` · ${inv.contactType}` : ''}
                </span>
              </div>
              <div className="code-meta">
                {inv.consumedAt ? (
                  <span className="tag tag-good">Joined</span>
                ) : (
                  <span className="tag tag-warn">Pending</span>
                )}
                {!inv.consumedAt && (
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={busyInvite === inv.id}
                    onClick={() => void removeInvite(inv)}
                  >
                    Revoke
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Invite modal */}
      {iDraft && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setIDraft(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <span className="overline">New invite</span>
            <h2 className="modal-title">Onboard by phone or email</h2>
            {iError && (
              <div className="banner banner-error" role="alert">
                {iError}
              </div>
            )}
            <div className="field">
              <span className="field-label">Phone (with country code) or email</span>
              <input
                className="input"
                value={iDraft.contact}
                onChange={(e) => setI('contact', e.target.value)}
                placeholder="+919876543210 or name@example.com"
              />
            </div>
            <div className="field">
              <span className="field-label">Program</span>
              <select className="select" value={iDraft.programId} onChange={(e) => setI('programId', e.target.value)}>
                {programs.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name ?? p.id}
                  </option>
                ))}
              </select>
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setIDraft(null)}>
                Cancel
              </button>
              <button className="btn btn-forest" disabled={iSaving} onClick={() => void saveInvite()}>
                {iSaving ? 'Saving…' : 'Create invite'}
              </button>
            </div>
          </div>
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
              <div className="field-row">
                <input className="input" value={cDraft.code} onChange={(e) => setC('code', e.target.value.toUpperCase())} placeholder="JULY26" maxLength={24} autoCapitalize="characters" />
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setC('code', generateCode())}>
                  Generate
                </button>
              </div>
              <span className="field-hint">Members type this when they join. Not case-sensitive.</span>
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
