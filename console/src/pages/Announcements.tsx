import { useEffect, useState } from 'react'
import {
  addDoc,
  collection,
  onSnapshot,
  query,
  serverTimestamp,
  where,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { relativeTime, toDate } from '../lib/time'
import './Announcements.css'

interface Announcement {
  id: string
  programId?: string
  authorId?: string
  authorName?: string
  title?: string
  body?: string
  createdAt?: unknown
}

// Coaches reuse the same handful of message shapes far more often than they
// write from scratch - these just prefill the fields below, so a coach can
// still edit before posting rather than being locked into canned copy.
const ANNOUNCEMENT_TEMPLATES: { label: string; title: string; body: string }[] = [
  {
    label: "Session moved",
    title: "Today's session has moved",
    body: 'A quick update: today\'s session time/location has changed. Please check the program calendar for the new details.',
  },
  {
    label: 'Great turnout today',
    title: 'Great turnout today!',
    body: "Thank you to everyone who joined today - it was wonderful to see so many of you show up. Keep up the great work!",
  },
  {
    label: 'Reminder: bring readings',
    title: "Reminder for our next session",
    body: 'A friendly reminder to bring your recent readings (sugar, BP, or weight) to our next session so we can review your progress together.',
  },
  {
    label: 'Milestone reached',
    title: 'A milestone worth celebrating',
    body: 'Our batch has reached a real milestone together this month. Thank you all for your consistency and effort - it shows.',
  },
]

export default function Announcements() {
  const { user } = useAuth()
  const { programs, loading: programsLoading, error: programsError } = usePrograms()
  const [programId, setProgramId] = useState<string>('')
  const [items, setItems] = useState<Announcement[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [posting, setPosting] = useState(false)
  const [postError, setPostError] = useState<string | null>(null)
  const [posted, setPosted] = useState(false)

  // Default to the first program once loaded.
  useEffect(() => {
    if (!programId && programs.length > 0) setProgramId(programs[0].id)
  }, [programs, programId])

  useEffect(() => {
    if (!programId) {
      setItems([])
      return
    }
    setLoading(true)
    const unsub = onSnapshot(
      query(collection(db, 'announcements'), where('programId', '==', programId)),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Announcement, 'id'>) }))
        next.sort(
          (a, b) => (toDate(b.createdAt)?.getTime() ?? 0) - (toDate(a.createdAt)?.getTime() ?? 0),
        )
        setItems(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load announcements'))
      },
    )
    return unsub
  }, [programId])

  async function post() {
    if (!programId || !title.trim() || !body.trim()) return
    setPosting(true)
    setPostError(null)
    setPosted(false)
    try {
      await addDoc(collection(db, 'announcements'), {
        programId,
        authorId: user?.uid ?? null,
        authorName: user?.displayName ?? user?.email ?? 'Coach',
        title: title.trim(),
        body: body.trim(),
        createdAt: serverTimestamp(),
      })
      setTitle('')
      setBody('')
      setPosted(true)
    } catch (err) {
      setPostError(errText(err, 'Announcement could not be posted'))
    } finally {
      setPosting(false)
    }
  }

  const selectedName = programs.find((p) => p.id === programId)?.name ?? 'this batch'

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Announcements</span>
        <h1>Announcements</h1>
        <p className="page-lede">
          Post to a batch's Announcements room. Members read these — only coaches and
          admins can post.
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
      </div>

      {programId && (
        <div className="card composer">
          <span className="overline">New announcement</span>
          {postError && (
            <div className="banner banner-error" role="alert">
              {postError}
            </div>
          )}
          {posted && (
            <div className="banner banner-success" role="status">
              Posted to {selectedName}.
            </div>
          )}
          <div className="field">
            <span className="field-label">Start from a template (optional)</span>
            <select
              className="select"
              value=""
              onChange={(e) => {
                const chosen = ANNOUNCEMENT_TEMPLATES.find((t) => t.label === e.target.value)
                if (chosen) {
                  setTitle(chosen.title)
                  setBody(chosen.body)
                }
              }}
            >
              <option value="">Choose a template…</option>
              {ANNOUNCEMENT_TEMPLATES.map((t) => (
                <option key={t.label} value={t.label}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <span className="field-label">Title</span>
            <input
              className="input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="This week's focus"
            />
          </div>
          <div className="field">
            <span className="field-label">Message</span>
            <textarea
              className="textarea"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Share what's ahead — warm and clear."
            />
          </div>
          <div className="composer-actions">
            <button
              className="btn btn-forest"
              disabled={posting || !title.trim() || !body.trim()}
              onClick={() => void post()}
            >
              {posting ? 'Posting…' : 'Post announcement'}
            </button>
          </div>
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
          <p>Loading announcements…</p>
        </div>
      ) : items.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>📣</div>
          <p className="empty-title">No announcements yet</p>
          <p className="empty-sub">
            The first note you post to {selectedName} will appear here, newest first.
          </p>
        </div>
      ) : (
        <div className="ann-list">
          {items.map((a) => (
            <article key={a.id} className="card ann-card">
              <div className="ann-head">
                <h2 className="ann-title">{a.title ?? '(untitled)'}</h2>
                <span className="ann-time">{relativeTime(a.createdAt)}</span>
              </div>
              <p className="ann-body">{a.body}</p>
              <span className="ann-author">— {a.authorName ?? 'Coach'}</span>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
