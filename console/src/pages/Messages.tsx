import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { useInbox, useMemberNames } from '../lib/inbox'
import { relativeTime } from '../lib/time'
import ThreadPanel from './ThreadPanel'
import './Messages.css'

/** Everything members have asked their coach, and a place to answer it. */
export default function Messages() {
  const { user } = useAuth()
  const { threads, loading, error } = useInbox()
  const [filter, setFilter] = useState<'needs' | 'all'>('needs')
  const [search, setSearch] = useState('')
  const [openKey, setOpenKey] = useState<string | null>(null)

  const unnamed = useMemo(() => threads.filter((t) => !t.memberName).map((t) => t.memberUid), [threads])
  const fetchedNames = useMemberNames(unnamed)
  const nameOf = (t: (typeof threads)[number]) => t.memberName ?? fetchedNames[t.memberUid] ?? 'Member'

  const waiting = threads.filter((t) => t.awaitingReply).length
  const visible = threads
    .filter((t) => (filter === 'needs' ? t.awaitingReply : true))
    .filter((t) => nameOf(t).toLowerCase().includes(search.trim().toLowerCase()))
  const open = threads.find((t) => t.key === openKey) ?? null

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Care+</span>
        <h1>Messages</h1>
        <p className="page-lede">Private questions from members to their coach, and your replies. Members get a push when you answer.</p>
      </header>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      <div className={'msg-layout' + (open ? ' msg-layout-open' : '')}>
        <div className="card msg-list">
          <div className="msg-list-top">
            <div className="seg" role="tablist">
              <button className={'seg-btn' + (filter === 'needs' ? ' seg-btn-active' : '')} onClick={() => setFilter('needs')}>
                Needs a reply ({waiting})
              </button>
              <button className={'seg-btn' + (filter === 'all' ? ' seg-btn-active' : '')} onClick={() => setFilter('all')}>
                All ({threads.length})
              </button>
            </div>
            <input className="input" placeholder="Search by member name" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search by member name" />
          </div>

          {loading ? (
            <div className="empty">
              <div className="spin spinner" aria-hidden />
              <p>Loading messages…</p>
            </div>
          ) : visible.length === 0 ? (
            <div className="empty">
              <div className="empty-mark" aria-hidden>
                💬
              </div>
              <p className="empty-title">{filter === 'needs' ? 'Nothing waiting for you' : 'No conversations yet'}</p>
              <p className="empty-sub">
                {filter === 'needs' ? 'Every member who asked something has had an answer.' : 'When a member uses “Ask your coach” in the app, it appears here.'}
              </p>
            </div>
          ) : (
            <ul className="msg-threads">
              {visible.map((t) => (
                <li key={t.key}>
                  <button className={'msg-thread' + (t.key === openKey ? ' msg-thread-active' : '')} onClick={() => setOpenKey(t.key)}>
                    <span className="msg-thread-top">
                      <span className="msg-thread-name">{nameOf(t)}</span>
                      <span className="msg-thread-time">{relativeTime(t.last.createdAt)}</span>
                    </span>
                    <span className="msg-thread-snippet">
                      {t.awaitingReply ? '' : 'You: '}
                      {t.last.text}
                    </span>
                    {t.awaitingReply && <span className="tag tag-warn msg-thread-tag">Needs a reply</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="card msg-panel">
          {open ? (
            <>
              <div className="msg-panel-head">
                <button className="btn btn-ghost btn-sm msg-back" onClick={() => setOpenKey(null)}>
                  ← All conversations
                </button>
                <h2>{nameOf(open)}</h2>
                <Link to={`/members/${open.memberUid}`} className="btn btn-ghost btn-sm">
                  Open health record
                </Link>
              </div>
              <ThreadPanel
                programId={open.programId}
                memberUid={open.memberUid}
                memberName={nameOf(open)}
                messages={open.messages}
                myUid={user?.uid ?? ''}
                myName={user?.displayName ?? 'Your coach'}
              />
            </>
          ) : (
            <div className="empty">
              <div className="empty-mark" aria-hidden>
                👈
              </div>
              <p className="empty-title">Choose a conversation</p>
              <p className="empty-sub">Members who are waiting for an answer are listed first.</p>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
