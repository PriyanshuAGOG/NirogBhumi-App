import { useEffect, useState } from 'react'
import {
  addDoc,
  collection,
  doc,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore'
import { db } from '../lib/firebase'
import { errText } from '../lib/errors'
import { relativeTime, toDate } from '../lib/time'
import './Content.css'

type Status = 'draft' | 'published'

interface ContentItem {
  id: string
  title?: string
  status?: Status
  body?: string
  url?: string
  createdAt?: unknown
  updatedAt?: unknown
}

interface Draft {
  id: string | null
  title: string
  status: Status
  url: string
  body: string
}

const empty: Draft = { id: null, title: '', status: 'draft', url: '', body: '' }

export default function Content() {
  const [items, setItems] = useState<ContentItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'all' | Status>('all')

  const [draft, setDraft] = useState<Draft | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  useEffect(() => {
    const unsub = onSnapshot(
      query(collection(db, 'contentItems')),
      (snap) => {
        const next = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<ContentItem, 'id'>) }))
        next.sort(
          (a, b) =>
            (toDate(b.updatedAt ?? b.createdAt)?.getTime() ?? 0) -
            (toDate(a.updatedAt ?? a.createdAt)?.getTime() ?? 0),
        )
        setItems(next)
        setLoading(false)
        setError(null)
      },
      (err) => {
        setLoading(false)
        setError(errText(err, 'Could not load content'))
      },
    )
    return unsub
  }, [])

  const visible = items.filter((i) => filter === 'all' || (i.status ?? 'draft') === filter)

  async function save() {
    if (!draft || !draft.title.trim()) {
      setSaveError('A title is required.')
      return
    }
    setSaving(true)
    setSaveError(null)
    const payload = {
      title: draft.title.trim(),
      status: draft.status,
      url: draft.url.trim() || null,
      body: draft.body.trim() || null,
      updatedAt: serverTimestamp(),
    }
    try {
      if (draft.id) {
        await setDoc(doc(db, 'contentItems', draft.id), payload, { merge: true })
      } else {
        await addDoc(collection(db, 'contentItems'), { ...payload, createdAt: serverTimestamp() })
      }
      setDraft(null)
    } catch (err) {
      setSaveError(errText(err, 'Content could not be saved'))
    } finally {
      setSaving(false)
    }
  }

  async function toggleStatus(item: ContentItem) {
    setBusyId(item.id)
    setError(null)
    const next: Status = (item.status ?? 'draft') === 'published' ? 'draft' : 'published'
    try {
      await updateDoc(doc(db, 'contentItems', item.id), {
        status: next,
        updatedAt: serverTimestamp(),
      })
    } catch (err) {
      setError(errText(err, 'Could not update the item'))
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
        <span className="overline">Content</span>
        <h1>Learn feed</h1>
        <p className="page-lede">
          Author the articles members read in Learn. Publish when ready, or keep a draft
          until it's polished.
        </p>
      </header>

      <div className="toolbar">
        <div className="seg" role="tablist">
          {(['all', 'published', 'draft'] as const).map((f) => (
            <button
              key={f}
              className={'seg-btn' + (filter === f ? ' seg-btn-active' : '')}
              onClick={() => setFilter(f)}
            >
              {f === 'all' ? 'All' : f === 'published' ? 'Published' : 'Drafts'}
            </button>
          ))}
        </div>
        <div className="toolbar-spacer" />
        <button
          className="btn btn-forest"
          onClick={() => {
            setDraft({ ...empty })
            setSaveError(null)
          }}
        >
          + New item
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
          <p>Loading content…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>📚</div>
          <p className="empty-title">Nothing here yet</p>
          <p className="empty-sub">
            {filter === 'all'
              ? 'Create your first Learn article.'
              : `No ${filter} items right now.`}
          </p>
        </div>
      ) : (
        <div className="content-list">
          {visible.map((item) => {
            const status = item.status ?? 'draft'
            return (
              <div key={item.id} className="card content-row">
                <div className="content-main">
                  <span className="content-title">{item.title ?? '(untitled)'}</span>
                  <span className="content-sub">
                    Updated {relativeTime(item.updatedAt ?? item.createdAt)}
                    {item.url ? ' · has link' : ''}
                  </span>
                </div>
                <div className="content-meta">
                  <span className={`tag ${status === 'published' ? 'tag-good' : 'tag-neutral'}`}>
                    {status === 'published' ? 'Published' : 'Draft'}
                  </span>
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={busyId === item.id}
                    onClick={() => void toggleStatus(item)}
                  >
                    {status === 'published' ? 'Unpublish' : 'Publish'}
                  </button>
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={() => {
                      setDraft({
                        id: item.id,
                        title: item.title ?? '',
                        status,
                        url: item.url ?? '',
                        body: item.body ?? '',
                      })
                      setSaveError(null)
                    }}
                  >
                    Edit
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {draft && (
        <div className="modal-scrim" role="dialog" aria-modal onClick={() => setDraft(null)}>
          <div className="modal card content-modal" onClick={(e) => e.stopPropagation()}>
            <span className="overline">{draft.id ? 'Edit item' : 'New item'}</span>
            <h2 className="modal-title">{draft.id ? 'Edit article' : 'New article'}</h2>
            {saveError && (
              <div className="banner banner-error" role="alert">
                {saveError}
              </div>
            )}
            <div className="field">
              <span className="field-label">Title</span>
              <input className="input" value={draft.title} onChange={(e) => set('title', e.target.value)} />
            </div>
            <div className="field">
              <span className="field-label">Link URL (optional)</span>
              <input className="input" value={draft.url} onChange={(e) => set('url', e.target.value)} placeholder="https://…" />
            </div>
            <div className="field">
              <span className="field-label">Body (optional)</span>
              <textarea className="textarea" value={draft.body} onChange={(e) => set('body', e.target.value)} />
            </div>
            <label className="check">
              <input
                type="checkbox"
                checked={draft.status === 'published'}
                onChange={(e) => set('status', e.target.checked ? 'published' : 'draft')}
              />
              Published (visible to members)
            </label>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setDraft(null)}>
                Cancel
              </button>
              <button className="btn btn-forest" disabled={saving} onClick={() => void save()}>
                {saving ? 'Saving…' : draft.id ? 'Save changes' : 'Create item'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
