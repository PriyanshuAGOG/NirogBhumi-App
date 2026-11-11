import { useState } from 'react'
import { doc, serverTimestamp, updateDoc } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useAuth } from '../auth/AuthProvider'
import { errText } from '../lib/errors'
import { relativeTime } from '../lib/time'
import { describeScreen, kindOf, useErrorReports, type ErrorReport } from '../lib/errorReports'
import './Support.css'

function shortId(id: string | null | undefined): string {
  if (!id) return 'not signed in'
  return id.length > 8 ? `${id.slice(0, 6)}…` : id
}

/**
 * Every CloudResult.Failure the Android app ever surfaces to a real user
 * also gets reported here (see MainActivity's single global cloudMessage
 * choke point) - this is the one place failures that only ever happen on
 * a real device are actually visible, instead of needing to be reproduced
 * blind from a bug description alone.
 */
export default function ErrorReports() {
  const { user } = useAuth()
  const { reports: items, loading, error } = useErrorReports(true)
  const [filter, setFilter] = useState<'open' | 'sign_in' | 'crash' | 'resolved' | 'all'>('open')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const visible = items.filter((i) => {
    if (filter === 'all') return true
    if (filter === 'resolved') return i.resolved === true
    if (filter === 'sign_in') return i.resolved !== true && kindOf(i) === 'sign_in'
    if (filter === 'crash') return i.resolved !== true && kindOf(i) === 'crash'
    return i.resolved !== true
  })

  async function resolve(item: ErrorReport) {
    setBusyId(item.id)
    setActionError(null)
    try {
      await updateDoc(doc(db, 'errorReports', item.id), {
        resolved: true,
        reviewedBy: user?.uid ?? null,
        reviewedAt: serverTimestamp(),
      })
    } catch (err) {
      setActionError(errText(err, 'Could not mark resolved'))
    } finally {
      setBusyId(null)
    }
  }

  const openCount = items.filter((i) => i.resolved !== true).length
  const signInOpen = items.filter((i) => i.resolved !== true && kindOf(i) === 'sign_in').length

  return (
    <section className="page">
      <header className="page-head">
        <span className="overline">Diagnostics</span>
        <h1>Error reports</h1>
        <p className="page-lede">
          Every failure the app actually surfaced to a real member, reported automatically - no
          need to reproduce a bug blind. Includes problems before anyone signs in (the SMS code, a refused
          sign-in) and app crashes. {openCount} open right now{signInOpen > 0 ? `, ${signInOpen} of them sign-in problems` : ''}.
        </p>
      </header>

      <div className="toolbar">
        <div className="seg" role="tablist">
          {(['open', 'sign_in', 'crash', 'resolved', 'all'] as const).map((f) => (
            <button
              key={f}
              className={'seg-btn' + (filter === f ? ' seg-btn-active' : '')}
              onClick={() => setFilter(f)}
            >
              {f === 'open' ? 'Open' : f === 'sign_in' ? 'Sign-in problems' : f === 'crash' ? 'Crashes' : f === 'resolved' ? 'Resolved' : 'All'}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}
      {actionError && (
        <div className="banner banner-error" role="alert">
          {actionError}
        </div>
      )}

      {loading ? (
        <div className="card empty">
          <div className="spin spinner" aria-hidden />
          <p>Loading reports…</p>
        </div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <div className="empty-mark" aria-hidden>✅</div>
          <p className="empty-title">
            {filter === 'open' ? 'Nothing open right now' : 'Nothing here'}
          </p>
          <p className="empty-sub">
            {filter === 'open'
              ? 'New failures from real devices will appear here in real time.'
              : `No ${filter} reports right now.`}
          </p>
        </div>
      ) : (
        <div className="sup-list">
          {visible.map((item) => (
            <article key={item.id} className="card sup-card" data-kind={kindOf(item)}>
              <div className="sup-head">
                <span className="sup-subject">{describeScreen(item.screen)}</span>
                <span className="err-tags">
                  {(item.count ?? 1) > 1 && <span className="tag tag-warn" title="How many times this exact problem happened">{item.count}×</span>}
                  <span className={`tag ${item.resolved ? 'tag-good' : 'tag-warn'}`}>{item.resolved ? 'Resolved' : 'Open'}</span>
                </span>
              </div>
              {item.message && <p className="sup-message">{item.message}</p>}
              {item.code && <code className="sup-code">{item.code}</code>}
              {item.detail && <p className="sup-meta err-detail">{item.detail}</p>}
              <div className="sup-foot">
                <span className="sup-meta">
                  {shortId(item.userId)}
                  {item.device ? ` · ${item.device}` : ''}
                  {item.appVersion ? ` · app ${item.appVersion}` : ''}
                  {item.installs && item.installs.length > 1 ? ` · ${item.installs.length} phones` : ''}
                  {' · '}last {relativeTime(item.createdAt)}
                </span>
                {!item.resolved && (
                  <button
                    className="btn btn-forest btn-sm"
                    disabled={busyId === item.id}
                    onClick={() => void resolve(item)}
                  >
                    {busyId === item.id ? 'Working…' : 'Mark resolved'}
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
