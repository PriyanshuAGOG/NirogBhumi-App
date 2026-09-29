// Consultation requests: a member asks (status 'pending'), staff confirm a time in
// the console (status 'confirmed' + scheduledAt/expert/link), and this module turns
// each change into the right push - confirmed, rescheduled, declined, cancelled -
// plus a one-hour-before reminder that is replaced or removed whenever the
// appointment changes, so nobody is reminded of a session that moved or was cancelled.
import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';

const REMINDER_LEAD_MS = 60 * 60 * 1000;

const whenIST = (ms: number) => new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true }).format(new Date(ms));
const millisOf = (value: unknown): number | null => (value && typeof (value as Timestamp).toMillis === 'function' ? (value as Timestamp).toMillis() : null);
const cleanType = (value: unknown) => String(value ?? '').trim() || 'consultation';

export async function handleConsultationChange(
  db: Firestore,
  id: string,
  before: Record<string, unknown> | undefined,
  after: Record<string, unknown> | undefined,
  now = Date.now(),
): Promise<void> {
  if (!after) return;
  const userId = String(after.userId ?? '');
  if (!userId) return;
  const statusBefore = String(before?.status ?? '');
  const statusAfter = String(after.status ?? '');
  const statusChanged = statusBefore !== statusAfter;
  const scheduledBefore = millisOf(before?.scheduledAt);
  const scheduledAfter = millisOf(after.scheduledAt);
  const scheduleChanged = scheduledBefore !== scheduledAfter;
  if (!statusChanged && !scheduleChanged) return; // e.g. the member edited their own free text

  const push = (title: string, body: string, kind: string, scheduledFor: Timestamp | FirebaseFirestore.FieldValue) =>
    db.collection('notifications').add({ userId, profileId: null, title, body, type: 'consultation', kind, consultationId: id, status: 'scheduled', scheduledFor, createdAt: FieldValue.serverTimestamp() });

  // Whenever the appointment stops being exactly what a queued reminder describes, drop the reminder.
  const stale = await db.collection('notifications').where('consultationId', '==', id).where('status', '==', 'scheduled').get();
  const batch = db.batch();
  stale.docs.filter(d => d.get('kind') === 'reminder').forEach(d => batch.delete(d.ref));
  await batch.commit();

  const type = cleanType(after.consultationType);
  if (statusAfter === 'confirmed' && scheduledAfter) {
    const expert = String(after.expertName ?? '').trim();
    const details = `${type} · ${whenIST(scheduledAfter)}${expert ? ` with ${expert}` : ''}`;
    await push(statusBefore === 'confirmed' ? 'Consultation rescheduled' : 'Consultation confirmed', details, 'update', FieldValue.serverTimestamp());
    if (scheduledAfter - REMINDER_LEAD_MS > now) {
      await push('Your consultation starts in an hour', `${type} at ${whenIST(scheduledAfter)}`, 'reminder', Timestamp.fromMillis(scheduledAfter - REMINDER_LEAD_MS));
    }
    return;
  }
  if (!statusChanged) return;
  if (statusAfter === 'declined') {
    const reason = String(after.declineReason ?? '').trim();
    await push("We couldn't schedule your consultation", reason || 'Please reach out to support and we will find another time.', 'update', FieldValue.serverTimestamp());
  } else if (statusAfter === 'cancelled' && after.cancelledBy !== 'member') {
    await push('Your consultation was cancelled', `${type}${scheduledBefore ? ` on ${whenIST(scheduledBefore)}` : ''}. Please contact support to rebook.`, 'update', FieldValue.serverTimestamp());
  }
}

export interface CancelResult { cancelled: boolean; alreadyCancelled: boolean }

/** A member withdraws their own request or confirmed booking (they can't edit status directly). */
export async function cancelOwnConsultation(db: Firestore, uid: string, id: string): Promise<CancelResult> {
  const ref = db.doc(`consultations/${id}`);
  return db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw Object.assign(new Error('not-found'), { code: 'not-found' });
    if (snap.get('userId') !== uid) throw Object.assign(new Error('permission-denied'), { code: 'permission-denied' });
    const status = String(snap.get('status'));
    if (status === 'cancelled') return { cancelled: false, alreadyCancelled: true };
    if (!['pending', 'payment_pending', 'confirmed'].includes(status)) throw Object.assign(new Error(`failed-precondition:${status}`), { code: 'failed-precondition' });
    tx.update(ref, { status: 'cancelled', cancelledBy: 'member', cancelledAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
    return { cancelled: true, alreadyCancelled: false };
  });
}
