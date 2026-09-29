import { useEffect, useMemo, useState } from 'react'
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
  where,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { usePrograms } from '../lib/usePrograms'
import { errText } from '../lib/errors'
import { relativeTime, toDate } from '../lib/time'
import './Resources.css'

type Category = 'diet' | 'yoga' | 'naturopathy' | 'guidance' | 'other'

const CATEGORIES: { key: Category; label: string; hint: string }[] = [
  { key: 'diet', label: 'Diet plan', hint: 'Meals for the week, swaps, portions - in plain, familiar foods.' },
  { key: 'yoga', label: 'Yoga', hint: 'A routine: warm-up, poses, breathing, duration, and when to stop.' },
  { key: 'naturopathy', label: 'Naturopathy', hint: 'A supportive practice with steps, materials, and when to avoid it.' },
  { key: 'guidance', label: 'Guidance', hint: 'A note for the batch: this week\'s focus, a reminder, a how-to.' },
  { key: 'other', label: 'Other', hint: 'Anything else your batch should be able to find later.' },
]
const labelOf = (c?: string) => CATEGORIES.find((x) => x.key === c)?.label ?? 'Resource'

const TITLE_MAX = 120
const BODY_MAX = 5000

interface Resource {
  id: string
  programId?: string
  category?: Category
  title?: string
  body?: string
  link?: string | null
  weekNumber?: number | null
  createdByName?: string
  createdAt?: unknown
  updatedAt?: unknown
}

interface Draft {
  id: string | null
  category: Category
  title: string
  body: string
  link: string
  weekNumber: string
  notify: boolean
}
const emptyDraft: Draft = { id: null, category: 'diet', title: '', body: '', link: '', weekNumber: '', notify: true }

function safeHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

/**
 * Plans and guidance for one batch - diet plans, yoga and naturopathy routines,
 * notes. Members see these in the app under Care+ > Plans & guidance. Only admin
 * or the batch's own coach can write here (enforced by the security rules, not
 * just this page), and members of the batch get a push when something new lands.
 */
export default function Resources() {
  const { user } = useAuth()
  const { programs, loading: programsLoading, error: programsError } = usePrograms()
  const [programId, setProgramId] = useState('')
  const [items, setItems] = useState<Resource[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'all' | Category>('all')
  const [draft, setDraft] = useState<Draft | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Resource | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

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
      query(collection(db, 'programResources'), where('programId', '==', programId)),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Resource, 'id'>) }))
        // Week 1 first, then newest; sorted here so no composite index is needed.
        next.sort((a, b) => (a.weekNumber ?? 999) - (b.weekNumber ?? 999) || (toDate(b.createdAt)?.getTime() ?? 0) - (toDate(a.createdAt)?.getTime() ?? 0))
        setItems(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load resources'))
      },
    )
    return unsub
  }, [programId])

  const visible = useMemo(() => items.filter((i) => filter === 'all' || i.category === filter), [items, filter])
  const programName = programs.find((p) => p.id === programId)?.name ?? 'this batch'

  function set<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  async function save() {
    if (!draft || !programId) return
    const title = draft.title.trim()
    const body = draft.body.trim()
    const link = draft.link.trim()
    const week = draft.weekNumber.trim()
    if (!title) return setSaveError('Give it a short title.')
    if (title.length > TITLE_MAX) return setSaveError(`Keep the title under ${TITLE_MAX} characters.`)
    if (!body && !link) return setSaveError('Add some text or a link so members have something to open.')
    if (body.length > BODY_MAX) return setSaveError(`The text is over ${BODY_MAX} characters - split it into two resources.`)
    if (link && !safeHttpUrl(link)) return setSaveError('The link must start with http:// or https://')
    if (week && !/^\d{1,3}$/.test(week)) return setSaveError('Week must be a number (or leave it empty).')
    setSaving(true)
    setSaveError(null)
    const fields = {
      programId,
      category: draft.category,
      title,
      body,
      link: link || null,
      weekNumber: week ? Number(week) : null,
    }
    try {
      if (draft.id) {
        // Keep the original author/creation time/notify flag: the rules pin them.
        const original = items.find((i) => i.id === draft.id)
        await updateDoc(doc(db, 'programResources', draft.id), {
          ...fields,
          notify: false,
          createdBy: (original as { createdBy?: string } | undefined)?.createdBy ?? user?.uid,
          createdByName: original?.createdByName ?? user?.displayName ?? null,
          createdAt: original?.createdAt ?? serverTimestamp(),
          updatedAt: serverTimestamp(),
        })
        setNotice('Saved. Members see the update straight away.')
      } else {
        await addDoc(collection(db, 'programResources'), {
          ...fields,
          notify: draft.notify,
          createdBy: user?.uid ?? null,
          createdByName: user?.displayName ?? user?.email ?? 'Your coach',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        })
        setNotice(draft.notify ? `Shared with ${programName}. Members get a notification shortly.` : `Shared with ${programName} quietly (no notification).`)
      }
      setDraft(null)
    } catch (err) {
      setSaveError(errText(err, 'Could not save this resource'))
    } finally {
      setSaving(false)
    }
  }

  async function remove(item: Resource) {
    setBusyId(item.id)
    try {
      await deleteDoc(doc(db, 'programResources', item.id))
      setNotice('Removed.')
      setConfirmDelete(null)
    } catch (err) {
      setError(errText(err, 'Could not delete this resource'))
      setConfirmDelete(null)
    } finally {
      setBusyId(null)
    }
  }

  const hint = CATEGORIES.find((c) => c.key === draft?.category)?.hint ?? ''

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Care+</span>
        <h1>Plans &amp; guidance</h1>
        <p className="page-lede">
          Diet plans, yoga and naturopathy routines, and notes for a batch. Members find them in the app
          under Care+, and get a notification when you add something new.
        </p>
      </header>

      {programsError && <div className="banner banner-error" role="alert">{programsError}</div>}

      <div className="toolbar">
        <div className="field">
          <span className="field-label">Batch</span>
          <select className="select" value={programId} onChange={(e) => setProgramId(e.target.value)} disabled={programsLoading || programs.length === 0}>
            {programs.length === 0 && <option value="">No programs yet</option>}
            {programs.map((p) => (
              <option key={p.id} value={p.id}>{p.name ?? p.id}</option>
            ))}
          </select>
        </div>
        <div className="toolbar-spacer" />
        {programId && (
          <button className="btn btn-forest" onClick={() => { setDraft({ ...emptyDraft }); setSaveError(null) }}>
            + New resource
          </button>
        )}
      </div>

      {programId && (
        <div className="res-cats">
          <div className="seg" role="tablist">
            {(['all', ...CATEGORIES.map((c) => c.key)] as const).map((key) => (
              <button key={key} className={'seg-btn' + (filter === key ? ' seg-btn-active' : '')} onClick={() => setFilter(key)}>
                {key === 'all' ? 'All' : labelOf(key)}
              </button>
            ))}
          </div>
        </div>
      )}

      {notice && <div className="banner banner-success" role="status">{notice}</div>}
      {error && <div className="banner banner-error" role="alert">{error}</div>}

      {loading ? (
        <div className="card empty"><div className="spin spinner" aria-hidden /><p>Loading…</p></div>
      ) : !programId ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>🌱</div>
          <p className="empty-title">No batch to add resources to</p>
          <p className="empty-sub">Create a program first, then share plans with its members here.</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>📋</div>
          <p className="empty-title">{filter === 'all' ? `Nothing shared with ${programName} yet` : `No ${labelOf(filter).toLowerCase()} resources yet`}</p>
          <p className="empty-sub">Add a first diet plan or routine and members will see it in their Care+ tab.</p>
        </div>
      ) : (
        <div className="res-list">
          {visible.map((item) => (
            <article key={item.id} className="card res-card">
              <div className="res-head">
                <span className="res-title">{item.title}</span>
                <span className="tag tag-neutral">{labelOf(item.category)}</span>
                {item.weekNumber != null && <span className="tag tag-good">Week {item.weekNumber}</span>}
              </div>
              {item.body && <p className="res-body">{item.body}</p>}
              <div className="res-foot">
                {item.link && <a href={item.link} target="_blank" rel="noreferrer noopener">Open link ↗</a>}
                <span>{item.createdByName ?? 'Coach'} · {relativeTime(item.updatedAt ?? item.createdAt)}</span>
                <span className="res-actions">
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => {
                      setDraft({ id: item.id, category: item.category ?? 'other', title: item.title ?? '', body: item.body ?? '', link: item.link ?? '', weekNumber: item.weekNumber != null ? String(item.weekNumber) : '', notify: false })
                      setSaveError(null)
                    }}
                  >
                    Edit
                  </button>
                  <button className="btn btn-ghost btn-sm" disabled={busyId === item.id} onClick={() => setConfirmDelete(item)}>Delete</button>
                </span>
              </div>
            </article>
          ))}
        </div>
      )}

      {draft && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => !saving && setDraft(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <span className="overline">{draft.id ? 'Edit resource' : `For ${programName}`}</span>
            <h2 className="modal-title">{draft.id ? 'Edit resource' : 'Share a plan or routine'}</h2>
            {saveError && <div className="banner banner-error" role="alert">{saveError}</div>}
            <div className="field-row">
              <div className="field">
                <span className="field-label">Type</span>
                <select className="select" value={draft.category} onChange={(e) => set('category', e.target.value as Category)}>
                  {CATEGORIES.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}
                </select>
              </div>
              <div className="field">
                <span className="field-label">Week (optional)</span>
                <input className="input" inputMode="numeric" value={draft.weekNumber} onChange={(e) => set('weekNumber', e.target.value.replace(/\D/g, '').slice(0, 3))} placeholder="e.g. 1" />
              </div>
            </div>
            <div className="field">
              <span className="field-label">Title</span>
              <input className="input" value={draft.title} maxLength={TITLE_MAX} onChange={(e) => set('title', e.target.value)} placeholder="Week 1 - building your plate" autoFocus />
            </div>
            <div className="field">
              <span className="field-label">Details</span>
              <textarea className="textarea" rows={9} value={draft.body} onChange={(e) => set('body', e.target.value)} placeholder={hint} />
              <span className={'res-counter' + (draft.body.length > BODY_MAX ? ' res-counter-over' : '')}>{draft.body.length} / {BODY_MAX}</span>
            </div>
            <div className="field">
              <span className="field-label">Link (optional)</span>
              <input className="input" value={draft.link} onChange={(e) => set('link', e.target.value)} placeholder="https://…" inputMode="url" />
            </div>
            {!draft.id && (
              <label className="check">
                <input type="checkbox" checked={draft.notify} onChange={(e) => set('notify', e.target.checked)} />
                Notify the batch
              </label>
            )}
            <div className="modal-actions">
              <button className="btn btn-ghost" disabled={saving} onClick={() => setDraft(null)}>Cancel</button>
              <button className="btn btn-forest" disabled={saving} onClick={() => void save()}>
                {saving ? 'Saving…' : draft.id ? 'Save changes' : 'Share with batch'}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDelete && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setConfirmDelete(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">Remove “{confirmDelete.title}”?</h2>
            <p className="page-lede">Members will no longer see it in the app.</p>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setConfirmDelete(null)}>Keep it</button>
              <button className="btn btn-forest" disabled={busyId === confirmDelete.id} onClick={() => void remove(confirmDelete)}>Remove</button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
