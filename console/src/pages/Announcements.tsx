import { useEffect, useMemo, useState } from 'react'
import { collection, onSnapshot, orderBy, query } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { relativeTime, toDate } from '../lib/time'
import {
  AUDIENCE_LABELS,
  callCreateAnnouncement,
  callDeleteAnnouncement,
  callPreviewAnnouncementAudience,
  describeAudience,
  type AnnouncementAudience,
  type AudienceScope,
} from '../lib/announcements'
import './Announcements.css'

interface Announcement {
  id: string
  authorId?: string
  authorName?: string
  title?: string
  body?: string
  createdAt?: unknown
  expiresAt?: unknown
  audience?: AnnouncementAudience
  channels?: { inApp?: boolean; push?: boolean; email?: boolean }
  recipientCount?: number
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

const SCOPES_FOR_COACH: AudienceScope[] = ['program']
const SCOPES_FOR_ADMIN: AudienceScope[] = ['program', 'all_enrolled', 'all_users', 'inactive', 'non_enrolled']

function expiryLabel(value: unknown): string {
  const date = toDate(value)
  if (!date) return ''
  const hoursLeft = Math.round((date.getTime() - Date.now()) / 3_600_000)
  if (hoursLeft <= 0) return 'Expired'
  if (hoursLeft < 24) return `Auto-removed in ${hoursLeft}h`
  return `Auto-removed in ${Math.round(hoursLeft / 24)}d`
}

export default function Announcements() {
  const { user, role } = useAuth()
  const isAdmin = role === 'admin' || role === 'super_admin'
  const { programs, loading: programsLoading, error: programsError } = usePrograms()

  const [items, setItems] = useState<Announcement[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [scope, setScope] = useState<AudienceScope>('program')
  const [selectedProgramIds, setSelectedProgramIds] = useState<string[]>([])
  const [inactiveDays, setInactiveDays] = useState(4)
  const [expiresInHours, setExpiresInHours] = useState(24)
  const [channels, setChannels] = useState({ inApp: true, push: true, email: false })

  const [previewCount, setPreviewCount] = useState<number | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [posting, setPosting] = useState(false)
  const [postError, setPostError] = useState<string | null>(null)
  const [posted, setPosted] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const availableScopes = isAdmin ? SCOPES_FOR_ADMIN : SCOPES_FOR_COACH

  useEffect(() => {
    setLoading(true)
    const unsub = onSnapshot(
      query(collection(db, 'announcements'), orderBy('createdAt', 'desc')),
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Announcement, 'id'>) })))
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load announcements'))
      },
    )
    return unsub
  }, [])

  // Recipient count preview goes stale the moment any targeting input
  // changes, so it's cleared rather than left showing a number that no
  // longer matches what's about to be sent.
  useEffect(() => {
    setPreviewCount(null)
  }, [scope, selectedProgramIds, inactiveDays])

  const audience: AnnouncementAudience = useMemo(
    () => ({ scope, programIds: selectedProgramIds, inactiveDays }),
    [scope, selectedProgramIds, inactiveDays],
  )

  const canSend =
    title.trim().length > 0 &&
    body.trim().length > 0 &&
    (scope !== 'program' || selectedProgramIds.length > 0) &&
    (channels.inApp || channels.push || channels.email)

  async function preview() {
    if (scope === 'program' && !selectedProgramIds.length) return
    setPreviewing(true)
    try {
      const count = await callPreviewAnnouncementAudience(audience)
      setPreviewCount(count)
    } catch (err) {
      setPostError(errText(err, 'Could not preview this audience'))
    } finally {
      setPreviewing(false)
    }
  }

  async function send() {
    if (!canSend) return
    setPosting(true)
    setPostError(null)
    setPosted(null)
    try {
      const result = await callCreateAnnouncement({
        title: title.trim(),
        body: body.trim(),
        audience,
        channels,
        expiresInHours,
      })
      setTitle('')
      setBody('')
      setSelectedProgramIds([])
      setPreviewCount(null)
      setPosted(`Sent to ${result.recipientCount} ${result.recipientCount === 1 ? 'person' : 'people'}.`)
    } catch (err) {
      setPostError(errText(err, 'Announcement could not be sent'))
    } finally {
      setPosting(false)
    }
  }

  async function remove(id: string) {
    setDeletingId(id)
    try {
      await callDeleteAnnouncement(id)
    } catch (err) {
      setError(errText(err, 'Could not delete this announcement'))
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Announcements</span>
        <h1>Announcements</h1>
        <p className="page-lede">
          Broadcast to exactly who needs it - one program, everyone enrolled, the whole app, or
          members who've gone quiet - across in-app, push, and email in any combination.
        </p>
      </header>

      {programsError && (
        <div className="banner banner-error" role="alert">
          {programsError}
        </div>
      )}

      <div className="card composer">
        <span className="overline">New announcement</span>
        {postError && (
          <div className="banner banner-error" role="alert">
            {postError}
          </div>
        )}
        {posted && (
          <div className="banner banner-success" role="status">
            {posted}
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

        <div className="perm-grid">
          <span className="perm-label">Who receives this</span>
          <div className="field">
            <select
              className="select"
              value={scope}
              onChange={(e) => setScope(e.target.value as AudienceScope)}
              disabled={availableScopes.length === 1}
            >
              {availableScopes.map((s) => (
                <option key={s} value={s}>
                  {AUDIENCE_LABELS[s]}
                </option>
              ))}
            </select>
          </div>

          {scope === 'program' && (
            <div className="perm-checks">
              {programsLoading && <span className="ann-author">Loading programs…</span>}
              {!programsLoading && programs.length === 0 && (
                <span className="ann-author">No programs yet</span>
              )}
              {programs.map((p) => (
                <label key={p.id} className="check">
                  <input
                    type="checkbox"
                    checked={selectedProgramIds.includes(p.id)}
                    onChange={(e) =>
                      setSelectedProgramIds((prev) =>
                        e.target.checked ? [...prev, p.id] : prev.filter((id) => id !== p.id),
                      )
                    }
                  />
                  {p.name ?? p.id}
                </label>
              ))}
            </div>
          )}

          {scope === 'inactive' && (
            <div className="field">
              <span className="field-label">Inactive for at least (days)</span>
              <input
                className="input"
                type="number"
                min={1}
                max={365}
                value={inactiveDays}
                onChange={(e) => setInactiveDays(Math.max(1, Number(e.target.value) || 4))}
                style={{ maxWidth: 120 }}
              />
            </div>
          )}
        </div>

        <div className="perm-grid">
          <span className="perm-label">Deliver via</span>
          <div className="perm-checks">
            <label className="check">
              <input
                type="checkbox"
                checked={channels.inApp}
                onChange={(e) => setChannels((c) => ({ ...c, inApp: e.target.checked }))}
              />
              In-app
            </label>
            <label className="check">
              <input
                type="checkbox"
                checked={channels.push}
                onChange={(e) => setChannels((c) => ({ ...c, push: e.target.checked }))}
              />
              Push notification
            </label>
            <label className="check">
              <input
                type="checkbox"
                checked={channels.email}
                onChange={(e) => setChannels((c) => ({ ...c, email: e.target.checked }))}
              />
              Email
            </label>
          </div>
          {channels.email && (
            <span className="ann-author">
              Email requires the Firebase "Trigger Email" extension to be installed and configured -
              until then these are queued but not delivered.
            </span>
          )}
        </div>

        <div className="field">
          <span className="field-label">Stays visible for (hours)</span>
          <input
            className="input"
            type="number"
            min={1}
            max={720}
            value={expiresInHours}
            onChange={(e) => setExpiresInHours(Math.max(1, Number(e.target.value) || 24))}
            style={{ maxWidth: 120 }}
          />
        </div>

        <div className="composer-actions">
          <button
            className="btn btn-ghost"
            disabled={previewing || (scope === 'program' && !selectedProgramIds.length)}
            onClick={() => void preview()}
          >
            {previewing ? 'Checking…' : previewCount === null ? 'Preview audience' : `Reaches ${previewCount} people`}
          </button>
          <button className="btn btn-forest" disabled={posting || !canSend} onClick={() => void send()}>
            {posting ? 'Sending…' : 'Send announcement'}
          </button>
        </div>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading announcements…</p>
        </div>
      ) : items.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>📣</div>
          <p className="empty-title">No announcements yet</p>
          <p className="empty-sub">The first one you send will appear here, newest first.</p>
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
              <div className="ann-head">
                <span className="ann-author">
                  — {a.authorName ?? 'Staff'} · {describeAudience(a.audience)}
                  {a.recipientCount != null ? ` · ${a.recipientCount} recipients` : ''}
                  {a.channels
                    ? ` · ${[a.channels.inApp && 'in-app', a.channels.push && 'push', a.channels.email && 'email']
                        .filter(Boolean)
                        .join(', ')}`
                    : ''}
                  {a.expiresAt ? ` · ${expiryLabel(a.expiresAt)}` : ''}
                </span>
                {(isAdmin || a.authorId === user?.uid) && (
                  <button
                    className="btn btn-danger btn-sm"
                    disabled={deletingId === a.id}
                    onClick={() => void remove(a.id)}
                  >
                    {deletingId === a.id ? 'Deleting…' : 'Delete'}
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
