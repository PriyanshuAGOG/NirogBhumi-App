import type { Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';

export interface MailMessage { to: string; subject: string; text: string; html?: string }
export type QueueResult = 'queued' | 'duplicate';

export const escapeHtml = (s: string) => s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string));

/** Headers must never carry line breaks (header injection); everything else is length-capped. */
export const oneLine = (s: unknown, max = 200) => String(s ?? '').replace(/[\u0000-\u001f\u007f\u2028\u2029]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);

export const looksLikeEmail = (s: string) => /^[^\s@<>"',;]{1,64}@[^\s@<>"',;]{1,255}\.[A-Za-z]{2,}$/.test(s);

/**
 * Email goes out through one place. Today that is the Firebase "Trigger Email from Firestore" extension: a
 * document in `mail/{key}` is sent by it. Switching provider means changing only this function.
 * `key` makes it idempotent: queuing the same key twice (a retried trigger) sends one email, never two.
 */
export async function queueEmail(db: Firestore, key: string, message: MailMessage): Promise<QueueResult> {
  if (!looksLikeEmail(message.to)) throw new Error('invalid recipient');
  const id = key.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 200);
  try {
    await db.collection('mail').doc(id).create({
      to: [message.to],
      message: { subject: oneLine(message.subject, 150), text: message.text, ...(message.html ? { html: message.html } : {}) },
      createdAt: FieldValue.serverTimestamp(),
    });
    return 'queued';
  } catch (error) {
    if ((error as { code?: number | string }).code === 6 || (error as { code?: string }).code === 'already-exists') return 'duplicate';
    throw error;
  }
}
