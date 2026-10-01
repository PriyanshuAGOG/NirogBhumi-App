import type { Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';
import { escapeHtml, looksLikeEmail, oneLine, queueEmail } from './email.js';

/**
 * Tells the support inbox about a new support request. The member's words are treated as untrusted text:
 * control characters removed from anything that becomes a header, lengths capped, and the HTML copy escaped.
 */
export async function notifySupportRequest(db: Firestore, requestId: string, supportEmail: string | undefined): Promise<'queued' | 'duplicate' | 'skipped_not_configured' | 'skipped_missing'> {
  const ref = db.doc(`supportRequests/${requestId}`);
  const snap = await ref.get();
  if (!snap.exists) return 'skipped_missing';
  if (!supportEmail || !looksLikeEmail(supportEmail)) {
    await ref.set({ emailStatus: 'skipped_not_configured' }, { merge: true });
    return 'skipped_not_configured';
  }
  const uid = String(snap.get('userId') ?? '');
  const user = uid ? await db.doc(`users/${uid}`).get() : null;
  const name = oneLine(user?.get('fullName') ?? 'A member', 80);
  const contact = [user?.get('email'), user?.get('phone') ?? user?.get('phoneNumber')].map(v => oneLine(v, 80)).filter(Boolean).join(' / ') || 'no contact on file';
  const subject = oneLine(snap.get('subject'), 120) || '(no subject)';
  const message = String(snap.get('message') ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').slice(0, 4000);
  const appVersion = oneLine(snap.get('appVersion'), 40);
  const text = [`New support request ${requestId}`, `From: ${name} (${contact})`, appVersion ? `App version: ${appVersion}` : '', '', `Subject: ${subject}`, '', message, '', 'Reply from the console Support page so the member is notified in the app.'].filter((l, i) => l !== '' || i > 0).join('\n');
  const html = `<p><b>New support request</b> ${escapeHtml(requestId)}</p><p>From: ${escapeHtml(name)} (${escapeHtml(contact)})</p>${appVersion ? `<p>App version: ${escapeHtml(appVersion)}</p>` : ''}<p><b>${escapeHtml(subject)}</b></p><pre style="white-space:pre-wrap;font-family:inherit">${escapeHtml(message)}</pre>`;
  const result = await queueEmail(db, `support_${requestId}`, { to: supportEmail, subject: `[Support] ${subject}`, text, html });
  await ref.set({ emailStatus: result === 'queued' ? 'sent_to_inbox' : 'duplicate_ignored', emailQueuedAt: FieldValue.serverTimestamp() }, { merge: true });
  return result;
}
