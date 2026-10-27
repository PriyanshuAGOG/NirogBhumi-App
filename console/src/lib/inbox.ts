import { useEffect, useMemo, useState } from 'react'
import { addDoc, collection, doc, getDoc, limit, onSnapshot, orderBy, query, serverTimestamp, where } from 'firebase/firestore'
import { db } from './firebase'
import { chunk } from './usePrograms'
import { useScope } from './scope'
import { errText } from './errors'
import { toDate } from './time'

/** One message in a member's private thread with their program's coach (the app's "Ask your coach"). */
export interface InboxMessage {
  id: string
  programId: string
  memberUid: string
  fromUid: string
  senderName?: string
  senderRole?: string
  text: string
  createdAt: unknown
}

export interface Thread {
  key: string
  programId: string
  memberUid: string
  /** Oldest first. */
  messages: InboxMessage[]
  last: InboxMessage
  /** The member wrote last, so they are waiting for an answer. */
  awaitingReply: boolean
  /** Their own name when they have written (their messages carry it); the page fills in the rest. */
  memberName?: string
}

const MAX_TEXT = 2000

export function messageMillis(m: InboxMessage): number {
  // A message just sent has no server time yet: treat it as the newest.
  return toDate(m.createdAt)?.getTime() ?? Number.MAX_SAFE_INTEGER
}

/** Groups messages into one thread per (program, member). Threads waiting for a reply come first, then the most recent. */
export function buildThreads(messages: InboxMessage[]): Thread[] {
  const byKey = new Map<string, InboxMessage[]>()
  for (const m of messages) {
    if (!m.memberUid || !m.programId || !m.text) continue
    const key = `${m.programId}:${m.memberUid}`
    const list = byKey.get(key)
    if (list) list.push(m)
    else byKey.set(key, [m])
  }
  const threads: Thread[] = []
  for (const [key, list] of byKey) {
    list.sort((a, b) => messageMillis(a) - messageMillis(b))
    const last = list[list.length - 1]
    const fromMember = [...list].reverse().find((m) => m.fromUid === m.memberUid)
    threads.push({
      key,
      programId: last.programId,
      memberUid: last.memberUid,
      messages: list,
      last,
      awaitingReply: last.fromUid === last.memberUid,
      memberName: fromMember?.senderName,
    })
  }
  threads.sort((a, b) => Number(b.awaitingReply) - Number(a.awaitingReply) || messageMillis(b.last) - messageMillis(a.last))
  return threads
}

/**
 * Every thread this person may see: all of them for an admin, only their own programs' for a coach (Firestore refuses any
 * list that is not provably limited to the coach's programs, so the query says so).
 */
export function useInbox(): { threads: Thread[]; loading: boolean; error: string | null } {
  const { isAdmin, programIds, programKey, loading: scopeLoading } = useScope()
  const [messages, setMessages] = useState<InboxMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (scopeLoading) return
    if (!isAdmin && programIds.length === 0) {
      setMessages([])
      setLoading(false)
      return
    }
    const queries = isAdmin
      ? [query(collection(db, 'coachInboxMessages'), orderBy('createdAt', 'desc'), limit(500))]
      : chunk(programIds).map((ids) => query(collection(db, 'coachInboxMessages'), where('programId', 'in', ids), orderBy('createdAt', 'desc'), limit(300)))
    const perQuery = new Map<number, InboxMessage[]>()
    const unsubs = queries.map((q, index) =>
      onSnapshot(
        q,
        (snap) => {
          perQuery.set(index, snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<InboxMessage, 'id'>) })))
          setMessages(Array.from(perQuery.values()).flat())
          setError(null)
          setLoading(false)
        },
        (err) => {
          setError(errText(err, 'Could not load messages'))
          setLoading(false)
        },
      ),
    )
    return () => unsubs.forEach((u) => u())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, programKey, scopeLoading])

  const threads = useMemo(() => buildThreads(messages), [messages])
  return { threads, loading: loading || scopeLoading, error }
}

/** The names of members whose threads have no message from them yet (a coach started the conversation). */
export function useMemberNames(uids: string[]): Record<string, string> {
  const [names, setNames] = useState<Record<string, string>>({})
  const key = uids.join(',')
  useEffect(() => {
    const missing = uids.filter((u) => !(u in names))
    if (missing.length === 0) return
    let cancelled = false
    void Promise.all(
      missing.map(async (uid) => {
        try {
          const snap = await getDoc(doc(db, 'users', uid))
          const d = snap.data() ?? {}
          return [uid, String(d.fullName ?? d.name ?? d.email ?? 'Member')] as const
        } catch {
          return [uid, 'Member'] as const
        }
      }),
    ).then((entries) => {
      if (!cancelled) setNames((prev) => ({ ...prev, ...Object.fromEntries(entries) }))
    })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return names
}

/** Writes a staff reply in the same shape the app's "Ask your coach" screen reads; the backend pushes it to the member. */
export async function sendReply(args: {
  programId: string
  memberUid: string
  text: string
  fromUid: string
  senderName: string
}): Promise<void> {
  const text = args.text.trim().slice(0, MAX_TEXT)
  if (!text) throw new Error('Write a message first')
  await addDoc(collection(db, 'coachInboxMessages'), {
    programId: args.programId,
    memberUid: args.memberUid,
    fromUid: args.fromUid,
    senderName: args.senderName,
    senderRole: 'coach', // the app renders any staff reply as "coach"
    text,
    createdAt: serverTimestamp(),
  })
}

export const MESSAGE_MAX_LENGTH = MAX_TEXT
