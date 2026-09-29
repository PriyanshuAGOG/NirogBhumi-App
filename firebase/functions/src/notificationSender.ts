// Delivers due documents from the `notifications` collection as FCM pushes.
//
// Every push in the product (reminders, coach replies, announcements, weekly
// digests, deletion notices, critical-reading alerts) is a `notifications` doc
// with status 'scheduled'; this sweep, run every 15 minutes, is what sends them.
// It used to send at most 100 documents per run, one at a time - a campaign to
// 5,000 members would have taken half a day to finish - so it now sends up to
// 1,000 per run with bounded concurrency and stops cleanly before the function
// timeout, leaving the rest 'scheduled' for the next run.
import { FieldValue, Timestamp, type DocumentSnapshot, type Firestore } from 'firebase-admin/firestore';

export interface PushMessage {
  token: string;
  notification: { title: string; body: string };
  data: Record<string, string>;
}
export interface SenderDeps {
  db: Firestore;
  send(message: PushMessage): Promise<unknown>;
}
export interface SenderOptions {
  limit?: number;
  concurrency?: number;
  /** Stop starting new sends once this many ms have passed. */
  budgetMs?: number;
  now?: () => number;
}
export interface SenderSummary { considered: number; sent: number; deferred: number; failed: number; stoppedEarly: boolean }

const minutesOf = (value: unknown, fallback: number) => {
  const match = String(value ?? '').match(/^(\d{1,2}):(\d{2})$/);
  return match ? Number(match[1]) * 60 + Number(match[2]) : fallback;
};

/** Runs `fn` over `items` with at most `size` in flight; stops picking up new items when `shouldStop()` turns true. */
export async function mapPool<T>(items: T[], size: number, fn: (item: T) => Promise<void>, shouldStop: () => boolean = () => false): Promise<number> {
  let next = 0;
  let started = 0;
  const workers = Array.from({ length: Math.max(1, Math.min(size, items.length)) }, async () => {
    while (next < items.length && !shouldStop()) {
      const item = items[next++];
      started++;
      await fn(item);
    }
  });
  await Promise.all(workers);
  return started;
}

export async function processPendingNotifications(deps: SenderDeps, options: SenderOptions = {}): Promise<SenderSummary> {
  const { db } = deps;
  const clock = options.now ?? Date.now;
  const startedAt = clock();
  const summary: SenderSummary = { considered: 0, sent: 0, deferred: 0, failed: 0, stoppedEarly: false };
  const due = await db.collection('notifications').where('status', '==', 'scheduled').where('scheduledFor', '<=', Timestamp.fromMillis(clock())).limit(options.limit ?? 1000).get();
  summary.considered = due.size;
  const users = new Map<string, Promise<DocumentSnapshot>>();
  const userDoc = (uid: string) => {
    let pending = users.get(uid);
    if (!pending) { pending = db.doc(`users/${uid}`).get(); users.set(uid, pending); }
    return pending;
  };

  const handle = async (doc: DocumentSnapshot) => {
    const notification = doc.data() ?? {};
    try {
      const user = await userDoc(String(notification.userId));
      const preferences = user.get('notificationPreferences') ?? {};
      const timezone = String(user.get('timezone') ?? 'Asia/Kolkata');
      const parts = new Intl.DateTimeFormat('en-GB', { timeZone: timezone, hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date(clock()));
      const currentMinutes = Number(parts.find(part => part.type === 'hour')?.value ?? 0) * 60 + Number(parts.find(part => part.type === 'minute')?.value ?? 0);
      const quietStart = minutesOf(preferences.quietHoursStart, 21 * 60);
      const quietEnd = minutesOf(preferences.quietHoursEnd, 7 * 60);
      const inQuietHours = quietStart < quietEnd ? currentMinutes >= quietStart && currentMinutes < quietEnd : currentMinutes >= quietStart || currentMinutes < quietEnd;

      // Only routine reminders respect quiet hours and the daily cap; human
      // messages, announcements and critical-reading alerts always go out.
      if (notification.type === 'reminder' && inQuietHours) {
        await doc.ref.set({ scheduledFor: Timestamp.fromMillis(clock() + 60 * 60000), deferredReason: 'quiet_hours', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        summary.deferred++;
        return;
      }
      if (notification.type === 'reminder') {
        const startOfWindow = Timestamp.fromMillis(clock() - 24 * 60 * 60000);
        const sent = await db.collection('notifications').where('userId', '==', notification.userId).where('status', '==', 'sent').where('sentAt', '>=', startOfWindow).get();
        const maxReminders = Math.max(0, Math.min(5, Number(preferences.maxHealthReminders ?? 3)));
        if (sent.size >= maxReminders) {
          await doc.ref.set({ scheduledFor: Timestamp.fromMillis(clock() + 12 * 60 * 60000), deferredReason: 'daily_cap', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
          summary.deferred++;
          return;
        }
      }

      const token = user.get('fcmToken');
      if (!token) {
        await doc.ref.set({ status: 'failed', failureReason: 'missing_token', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        summary.failed++;
        return;
      }
      await deps.send({ token, notification: { title: String(notification.title ?? ''), body: String(notification.body ?? '') }, data: { type: String(notification.type ?? 'reminder'), notificationId: doc.id } });
      await doc.ref.set({ status: 'sent', sentAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      summary.sent++;
    } catch (error) {
      console.error('Notification send failed', doc.id, error);
      await doc.ref.set({ status: 'failed', failureReason: 'send_failed', updatedAt: FieldValue.serverTimestamp() }, { merge: true }).catch(() => {});
      summary.failed++;
    }
  };

  const started = await mapPool(due.docs, options.concurrency ?? 25, handle, () => options.budgetMs != null && clock() - startedAt > options.budgetMs);
  summary.stoppedEarly = started < due.size;
  return summary;
}
