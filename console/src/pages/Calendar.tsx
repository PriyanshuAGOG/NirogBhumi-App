import { useEffect, useMemo, useRef, useState } from 'react'
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  where,
  writeBatch,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { formatDate, formatTime, toDate, toInputDateTime, fromInputDateTime } from '../lib/time'
import { parseCsvRecords, toCsv, downloadCsv } from '../lib/csv'
import './Calendar.css'

type EventType = 'live' | 'walk' | 'lab' | 'qa'

interface ProgramEvent {
  id: string
  programId?: string
  title?: string
  type?: EventType
  startsAt?: unknown
  endsAt?: unknown
  location?: string
  link?: string
  description?: string
  bring?: string
  createdBy?: string
  updatedAt?: unknown
}

// Only http(s) links are ever written or rendered as a real href - any
// staff member (all of batches/announcements/calendar/programs is
// coach-writable, not just admin) could otherwise set link to a
// javascript: URI, which would execute in whichever admin's session later
// clicks "Join link" since it's rendered as a real <a href>.
function safeHttpUrl(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? value : null
  } catch {
    return null
  }
}

const TYPE_META: Record<EventType, { label: string; cls: string }> = {
  live: { label: 'Live session', cls: 'ev-live' },
  walk: { label: 'Group walk', cls: 'ev-walk' },
  lab: { label: 'Lab week', cls: 'ev-lab' },
  qa: { label: 'Q&A', cls: 'ev-qa' },
}

interface Draft {
  id: string | null
  title: string
  type: EventType
  startsAt: string
  endsAt: string
  location: string
  link: string
  description: string
  bring: string
}

function emptyDraft(): Draft {
  return {
    id: null,
    title: '',
    type: 'live',
    startsAt: '',
    endsAt: '',
    location: '',
    link: '',
    description: '',
    bring: '',
  }
}

function draftFrom(ev: ProgramEvent): Draft {
  return {
    id: ev.id,
    title: ev.title ?? '',
    type: ev.type ?? 'live',
    startsAt: toInputDateTime(ev.startsAt),
    endsAt: toInputDateTime(ev.endsAt),
    location: ev.location ?? '',
    link: ev.link ?? '',
    description: ev.description ?? '',
    bring: ev.bring ?? '',
  }
}

export default function Calendar() {
  const { user } = useAuth()
  const { programs, loading: programsLoading, error: programsError } = usePrograms()
  const [programId, setProgramId] = useState<string>('')
  const [events, setEvents] = useState<ProgramEvent[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [draft, setDraft] = useState<Draft | null>(null)
  const [alsoAnnounce, setAlsoAnnounce] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  useEffect(() => {
    if (!programId && programs.length > 0) setProgramId(programs[0].id)
  }, [programs, programId])

  useEffect(() => {
    if (!programId) {
      setEvents([])
      return
    }
    setLoading(true)
    const unsub = onSnapshot(
      query(collection(db, 'programEvents'), where('programId', '==', programId)),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ProgramEvent, 'id'>) }))
        next.sort(
          (a, b) => (toDate(a.startsAt)?.getTime() ?? 0) - (toDate(b.startsAt)?.getTime() ?? 0),
        )
        setEvents(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load events'))
      },
    )
    return unsub
  }, [programId])

  const grouped = useMemo(() => {
    const map = new Map<string, ProgramEvent[]>()
    for (const ev of events) {
      const key = formatDate(ev.startsAt)
      const bucket = map.get(key)
      if (bucket) bucket.push(ev)
      else map.set(key, [ev])
    }
    return Array.from(map.entries())
  }, [events])

  const programName = programs.find((p) => p.id === programId)?.name ?? 'this batch'

  async function save() {
    if (!draft || !programId) return
    const start = fromInputDateTime(draft.startsAt)
    if (!draft.title.trim() || !start) {
      setSaveError('A title and start time are required.')
      return
    }
    setSaving(true)
    setSaveError(null)
    const trimmedLink = draft.link.trim()
    if (trimmedLink && !safeHttpUrl(trimmedLink)) {
      setSaveError('Join link must be a valid http:// or https:// URL.')
      setSaving(false)
      return
    }
    const end = fromInputDateTime(draft.endsAt)
    const payload = {
      programId,
      title: draft.title.trim(),
      type: draft.type,
      startsAt: Timestamp.fromDate(start),
      endsAt: end ? Timestamp.fromDate(end) : null,
      location: draft.location.trim() || null,
      link: trimmedLink || null,
      description: draft.description.trim() || null,
      bring: draft.bring.trim() || null,
      updatedAt: serverTimestamp(),
    }
    try {
      if (draft.id) {
        await setDoc(doc(db, 'programEvents', draft.id), payload, { merge: true })
      } else {
        await addDoc(collection(db, 'programEvents'), {
          ...payload,
          createdBy: user?.uid ?? null,
        })
      }
      if (alsoAnnounce) {
        await addDoc(collection(db, 'announcements'), {
          programId,
          authorId: user?.uid ?? null,
          authorName: user?.displayName ?? user?.email ?? 'Coach',
          title: `Schedule update: ${draft.title.trim()}`,
          body: `${TYPE_META[draft.type].label} — ${formatDate(start)}, ${formatTime(start)}${
            draft.location.trim() ? ` at ${draft.location.trim()}` : ''
          }.`,
          createdAt: serverTimestamp(),
        })
      }
      setDraft(null)
      setAlsoAnnounce(false)
    } catch (err) {
      setSaveError(errText(err, 'Event could not be saved'))
    } finally {
      setSaving(false)
    }
  }

  function downloadEventTemplate() {
    const header = ['title', 'type', 'starts_at', 'ends_at', 'location', 'link', 'description', 'bring']
    const example = [
      'Saturday morning walk', 'walk', '2026-07-19T07:00', '2026-07-19T08:00',
      'Lakeside park', '', 'A relaxed group walk to close out the week.', 'Water bottle, walking shoes',
    ]
    downloadCsv('program_events_template.csv', toCsv([header, example]))
  }

  const csvInputRef = useRef<HTMLInputElement>(null)
  const [bulkBusy, setBulkBusy] = useState(false)
  const [bulkError, setBulkError] = useState<string | null>(null)
  const [bulkSummary, setBulkSummary] = useState<string | null>(null)

  async function handleEventCsv(file: File) {
    if (!programId) return
    setBulkBusy(true)
    setBulkError(null)
    setBulkSummary(null)
    try {
      const text = await file.text()
      const records = parseCsvRecords(text)
      if (records.length === 0) {
        setBulkError('That file has no data rows.')
        return
      }
      if (records.length > 300) {
        setBulkError('Too many rows in one file (max 300) - split it into smaller batches.')
        return
      }
      const validTypes: EventType[] = ['live', 'walk', 'lab', 'qa']
      const rowErrors: string[] = []
      const valid: Record<string, unknown>[] = []
      records.forEach((rec, idx) => {
        const rowNum = idx + 2 // header is row 1
        const title = (rec.title ?? '').trim()
        const type = (rec.type ?? 'live').trim().toLowerCase() as EventType
        const start = fromInputDateTime((rec.starts_at ?? '').trim())
        if (!title) { rowErrors.push(`Row ${rowNum}: missing title`); return }
        if (!validTypes.includes(type)) { rowErrors.push(`Row ${rowNum}: type must be one of live/walk/lab/qa`); return }
        if (!start) { rowErrors.push(`Row ${rowNum}: starts_at is missing or unreadable (use YYYY-MM-DDTHH:mm)`); return }
        const end = fromInputDateTime((rec.ends_at ?? '').trim())
        const link = (rec.link ?? '').trim()
        if (link && !safeHttpUrl(link)) { rowErrors.push(`Row ${rowNum}: link must be a valid http(s) URL`); return }
        valid.push({
          programId,
          title,
          type,
          startsAt: Timestamp.fromDate(start),
          endsAt: end ? Timestamp.fromDate(end) : null,
          location: (rec.location ?? '').trim() || null,
          link: link || null,
          description: (rec.description ?? '').trim() || null,
          bring: (rec.bring ?? '').trim() || null,
          createdBy: user?.uid ?? null,
          updatedAt: serverTimestamp(),
        })
      })
      if (valid.length === 0) {
        setBulkError(`No rows could be imported.\n${rowErrors.join('\n')}`)
        return
      }
      // Chunked the same way the backend chunks batched deletes elsewhere in
      // this codebase - a single writeBatch is capped at 500 operations.
      for (let offset = 0; offset < valid.length; offset += 400) {
        const batch = writeBatch(db)
        valid.slice(offset, offset + 400).forEach((payload) => batch.set(doc(collection(db, 'programEvents')), payload))
        await batch.commit()
      }
      setBulkSummary(
        `Added ${valid.length} event${valid.length === 1 ? '' : 's'}.` +
          (rowErrors.length ? ` Skipped ${rowErrors.length}: ${rowErrors.join('; ')}` : ''),
      )
    } catch (err) {
      setBulkError(errText(err, 'Could not import that file'))
    } finally {
      setBulkBusy(false)
      if (csvInputRef.current) csvInputRef.current.value = ''
    }
  }

  async function remove(ev: ProgramEvent) {
    setBusyId(ev.id)
    setError(null)
    try {
      await deleteDoc(doc(db, 'programEvents', ev.id))
    } catch (err) {
      setError(errText(err, 'Event could not be deleted'))
    } finally {
      setBusyId(null)
    }
  }

  function set<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Calendar</span>
        <h1>Program calendar</h1>
        <p className="page-lede">
          Live sessions, group walks, lab weeks and Q&amp;As for a batch. Editing an
          event can optionally post an announcement so schedule news never changes
          silently.
        </p>
      </header>

      {programsError && (
        <div className="banner banner-error" role="alert">
          {programsError}
        </div>
      )}

      <div className="toolbar">
        <div className="field">
          <span className="field-label">Batch</span>
          <select
            className="select"
            value={programId}
            onChange={(e) => setProgramId(e.target.value)}
            disabled={programsLoading || programs.length === 0}
          >
            {programs.length === 0 && <option value="">No programs yet</option>}
            {programs.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name ?? p.id}
              </option>
            ))}
          </select>
        </div>
        <div className="toolbar-spacer" />
        {programId && (
          <>
            <button className="btn btn-ghost" onClick={downloadEventTemplate}>
              Download CSV template
            </button>
            <button className="btn btn-ghost" disabled={bulkBusy} onClick={() => csvInputRef.current?.click()}>
              {bulkBusy ? 'Importing…' : 'Upload CSV'}
            </button>
            <input
              ref={csvInputRef}
              type="file"
              accept=".csv,text/csv"
              style={{ display: 'none' }}
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) void handleEventCsv(file)
              }}
            />
            <button
              className="btn btn-forest"
              onClick={() => {
                setDraft(emptyDraft())
                setAlsoAnnounce(false)
                setSaveError(null)
              }}
            >
              + Add event
            </button>
          </>
        )}
      </div>

      {bulkError && (
        <div className="banner banner-error" role="alert" style={{ whiteSpace: 'pre-line' }}>
          {bulkError}
        </div>
      )}
      {bulkSummary && (
        <div className="banner banner-success" role="status">
          {bulkSummary}
        </div>
      )}

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      {!programId ? null : loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading events…</p>
        </div>
      ) : events.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🗓️</div>
          <p className="empty-title">Nothing on the calendar yet</p>
          <p className="empty-sub">
            Add the first event for {programName} — members will see it in their
            program calendar.
          </p>
        </div>
      ) : (
        <div className="agenda">
          {grouped.map(([day, dayEvents]) => (
            <div key={day} className="agenda-day">
              <div className="agenda-date">{day}</div>
              <div className="agenda-items">
                {dayEvents.map((ev) => {
                  const meta = TYPE_META[ev.type ?? 'live'] ?? TYPE_META.live
                  return (
                    <article key={ev.id} className="card ev-card">
                      <div className="ev-time-col">
                        <span className="ev-time">{formatTime(ev.startsAt)}</span>
                        {toDate(ev.endsAt) && (
                          <span className="ev-time-end">{formatTime(ev.endsAt)}</span>
                        )}
                      </div>
                      <div className="ev-body">
                        <div className="ev-head">
                          <span className={`ev-type ${meta.cls}`}>{meta.label}</span>
                          <h3 className="ev-title">{ev.title ?? '(untitled)'}</h3>
                        </div>
                        {ev.description && <p className="ev-desc">{ev.description}</p>}
                        <div className="ev-meta">
                          {ev.location && <span>📍 {ev.location}</span>}
                          {ev.link && safeHttpUrl(ev.link) && (
                            <a href={ev.link} target="_blank" rel="noreferrer" className="ev-link">
                              Join link
                            </a>
                          )}
                          {ev.bring && <span>🎒 {ev.bring}</span>}
                        </div>
                      </div>
                      <div className="ev-actions">
                        <button
                          className="btn btn-ghost btn-sm"
                          onClick={() => {
                            setDraft(draftFrom(ev))
                            setAlsoAnnounce(false)
                            setSaveError(null)
                          }}
                        >
                          Edit
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={busyId === ev.id}
                          onClick={() => void remove(ev)}
                        >
                          {busyId === ev.id ? '…' : 'Delete'}
                        </button>
                      </div>
                    </article>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      {draft && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setDraft(null)}>
          <div className="modal card ev-modal" onClick={(e) => e.stopPropagation()}>
            <span className="overline">{draft.id ? 'Edit event' : 'New event'}</span>
            <h2 className="modal-title">{draft.id ? 'Edit event' : 'Add an event'}</h2>
            {saveError && (
              <div className="banner banner-error" role="alert">
                {saveError}
              </div>
            )}
            <div className="field">
              <span className="field-label">Title</span>
              <input
                className="input"
                value={draft.title}
                onChange={(e) => set('title', e.target.value)}
                placeholder="Saturday morning walk"
              />
            </div>
            <div className="field-row">
              <div className="field">
                <span className="field-label">Type</span>
                <select
                  className="select"
                  value={draft.type}
                  onChange={(e) => set('type', e.target.value as EventType)}
                >
                  <option value="live">Live session</option>
                  <option value="walk">Group walk</option>
                  <option value="lab">Lab week</option>
                  <option value="qa">Q&amp;A</option>
                </select>
              </div>
              <div className="field">
                <span className="field-label">Location</span>
                <input
                  className="input"
                  value={draft.location}
                  onChange={(e) => set('location', e.target.value)}
                  placeholder="Lakeside park / Online"
                />
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <span className="field-label">Starts</span>
                <input
                  className="input"
                  type="datetime-local"
                  value={draft.startsAt}
                  onChange={(e) => set('startsAt', e.target.value)}
                />
              </div>
              <div className="field">
                <span className="field-label">Ends (optional)</span>
                <input
                  className="input"
                  type="datetime-local"
                  value={draft.endsAt}
                  onChange={(e) => set('endsAt', e.target.value)}
                />
              </div>
            </div>
            <div className="field">
              <span className="field-label">Join link (optional)</span>
              <input
                className="input"
                value={draft.link}
                onChange={(e) => set('link', e.target.value)}
                placeholder="https://…"
              />
            </div>
            <div className="field">
              <span className="field-label">Description (optional)</span>
              <textarea
                className="textarea"
                value={draft.description}
                onChange={(e) => set('description', e.target.value)}
              />
            </div>
            <div className="field">
              <span className="field-label">What to bring (optional)</span>
              <input
                className="input"
                value={draft.bring}
                onChange={(e) => set('bring', e.target.value)}
                placeholder="Water bottle, walking shoes"
              />
            </div>
            {draft.id && (
              <label className="check">
                <input
                  type="checkbox"
                  checked={alsoAnnounce}
                  onChange={(e) => setAlsoAnnounce(e.target.checked)}
                />
                Also post an announcement about this change
              </label>
            )}
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setDraft(null)}>
                Cancel
              </button>
              <button className="btn btn-forest" disabled={saving} onClick={() => void save()}>
                {saving ? 'Saving…' : draft.id ? 'Save changes' : 'Create event'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
