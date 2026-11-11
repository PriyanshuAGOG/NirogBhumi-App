import { useEffect, useRef, useState } from 'react'
import { MESSAGE_MAX_LENGTH, sendReply, type InboxMessage } from '../lib/inbox'
import { errText } from '../lib/errors'
import { formatDateTime, relativeTime } from '../lib/time'
import './Messages.css'

interface Props {
  programId: string
  memberUid: string
  memberName: string
  messages: InboxMessage[]
  myUid: string
  myName: string
  /** Shown above the conversation when it is empty. */
  emptyHint?: string
}

/** One member's private conversation with their coach: read it, and answer it. */
export default function ThreadPanel({ programId, memberUid, memberName, messages, myUid, myName, emptyHint }: Props) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length, memberUid])

  async function send() {
    if (!text.trim() || busy) return
    setBusy(true)
    setError(null)
    try {
      await sendReply({ programId, memberUid, text, fromUid: myUid, senderName: myName })
      setText('')
    } catch (err) {
      setError(errText(err, 'Your message could not be sent'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="thread">
      <div className="thread-body" role="log" aria-label={`Conversation with ${memberName}`}>
        {messages.length === 0 && <p className="thread-empty">{emptyHint ?? 'No messages yet. Write the first one below.'}</p>}
        {messages.map((m) => {
          const fromMember = m.fromUid === memberUid
          return (
            <div key={m.id} className={'bubble-row' + (fromMember ? ' bubble-row-member' : ' bubble-row-staff')}>
              <div className={'bubble' + (fromMember ? ' bubble-member' : ' bubble-staff')}>
                <p className="bubble-text">{m.text}</p>
                <span className="bubble-meta" title={formatDateTime(m.createdAt)}>
                  {fromMember ? memberName : m.fromUid === myUid ? 'You' : m.senderName ?? 'Coach'} · {relativeTime(m.createdAt)}
                </span>
              </div>
            </div>
          )
        })}
        <div ref={endRef} />
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}
      <form
        className="thread-composer"
        onSubmit={(e) => {
          e.preventDefault()
          void send()
        }}
      >
        <textarea
          className="textarea"
          rows={2}
          placeholder={`Reply to ${memberName}…`}
          value={text}
          maxLength={MESSAGE_MAX_LENGTH}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
              e.preventDefault()
              void send()
            }
          }}
          aria-label="Your reply"
        />
        <div className="thread-actions">
          <span className="thread-hint">The member is notified on their phone. Ctrl+Enter sends.</span>
          <button type="submit" className="btn btn-forest btn-sm" disabled={busy || !text.trim()}>
            {busy ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </div>
  )
}
