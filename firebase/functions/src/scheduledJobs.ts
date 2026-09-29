// Work done by the daily maintenance schedule, extracted so it can be tested and
// so it scales: everything here pages through collections and runs with bounded
// concurrency instead of loading every user into one array / one write batch
// (a Firestore batch is limited to 500 writes, so the previous single-batch job
// would have started failing outright at 501 users).
import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { mapPool } from './notificationSender.js';

const IST = 'Asia/Kolkata';

/** YYYY-MM-DD in Indian Standard Time (en-CA formats as the sortable key we want). */
export function dayKeyIST(date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: IST, year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}
export const isMondayIST = (date = new Date()) => new Intl.DateTimeFormat('en-US', { timeZone: IST, weekday: 'short' }).format(date) === 'Mon';

/**
 * Batch Pulse collective goal: minutes walked together this calendar month, per
 * program, plus the opt-in leaderboard. Runs daily (a monthly total doesn't need
 * intraday freshness).
 */
export async function updateBatchPulse(db: Firestore, now = new Date()): Promise<number> {
  const programs = await db.collection('programs').get();
  const monthStart = new Date(now); monthStart.setDate(1); monthStart.setHours(0, 0, 0, 0);
  const monthStartTs = Timestamp.fromDate(monthStart);
  const today = dayKeyIST(now);
  let updated = 0;
  await mapPool(programs.docs, 5, async programDoc => {
    const programId = programDoc.id;
    const roster = await db.collection('programMembers').where('programId', '==', programId).get();
    const uids = roster.docs.map(m => String(m.get('uid') ?? '')).filter(Boolean);
    if (!uids.length) return;
    let totalMinutes = 0;
    // Per-uid breakdown alongside the team total - only ever surfaced to members
    // who opted in (below), never a silent default-on ranking.
    const minutesByUid: Record<string, number> = {};
    for (let i = 0; i < uids.length; i += 30) {
      const walks = await db.collection('walkLogs').where('userId', 'in', uids.slice(i, i + 30)).where('createdAt', '>=', monthStartTs).get();
      walks.docs.forEach(w => {
        const minutes = Number(w.get('minutes') ?? 0);
        if (!Number.isFinite(minutes)) return;
        totalMinutes += minutes;
        const uid = String(w.get('userId') ?? '');
        if (uid) minutesByUid[uid] = (minutesByUid[uid] ?? 0) + minutes;
      });
    }
    const leaderboard = roster.docs
      .filter(m => m.get('leaderboardOptIn') === true)
      .map(m => ({ name: String(m.get('name') ?? 'Member'), minutes: Math.round(minutesByUid[String(m.get('uid') ?? '')] ?? 0) }))
      .sort((a, b) => b.minutes - a.minutes)
      .slice(0, 10);
    await db.doc(`batchStats/${programId}_${today}`).set({ programId, dayKey: today, collectiveMinutes: Math.round(totalMinutes), memberCount: uids.length, leaderboard, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    updated++;
  });
  return updated;
}

export interface DigestOptions { pageSize?: number; concurrency?: number; now?: () => number; budgetMs?: number }
export interface DigestSummary { usersScanned: number; digestsQueued: number; stoppedEarly: boolean }

/**
 * Monday "your week in review" nudge: how many of the last 7 days the member
 * logged a reading or sleep, queued as a normal push. Never nags someone with
 * zero logged days. Users are read in pages of 300 by document id and processed
 * 20 at a time, so it stays inside the function timeout at any realistic size.
 */
export async function sendWeeklyDigests(db: Firestore, options: DigestOptions = {}): Promise<DigestSummary> {
  const clock = options.now ?? Date.now;
  const startedAt = clock();
  const pageSize = options.pageSize ?? 300;
  const end = Timestamp.fromMillis(clock());
  const start = Timestamp.fromMillis(end.toMillis() - 7 * 86400000);
  // Scheduled a few hours out (not at this 5am run) so it lands at a considerate
  // mid-morning hour rather than inside most users' default quiet hours (21:00-07:00).
  const scheduledFor = Timestamp.fromMillis(clock() + 4 * 60 * 60000);
  const summary: DigestSummary = { usersScanned: 0, digestsQueued: 0, stoppedEarly: false };

  let cursor: string | null = null;
  for (;;) {
    let q = db.collection('users').where('status', '==', 'active').orderBy('__name__').limit(pageSize);
    if (cursor) q = q.startAfter(cursor);
    const page = await q.get();
    if (page.empty) break;
    cursor = page.docs[page.docs.length - 1].id;
    summary.usersScanned += page.size;

    await mapPool(page.docs, options.concurrency ?? 20, async user => {
      const [glucose, sleep] = await Promise.all([
        db.collection('glucoseReadings').where('userId', '==', user.id).where('measuredAt', '>=', start).get(),
        db.collection('sleepLogs').where('userId', '==', user.id).where('createdAt', '>=', start).get(),
      ]);
      const loggedDayKeys = new Set<string>();
      for (const doc of [...glucose.docs, ...sleep.docs]) {
        const ts = (doc.get('measuredAt') ?? doc.get('createdAt')) as Timestamp | undefined;
        if (ts) loggedDayKeys.add(dayKeyIST(ts.toDate()));
      }
      const daysLogged = loggedDayKeys.size;
      if (daysLogged === 0) return;
      const body = daysLogged >= 4
        ? `Great rhythm - you logged health data on ${daysLogged} of the last 7 days. Keep it up!`
        : `You logged health data on ${daysLogged} of the last 7 days. Try logging one thing today to build your rhythm.`;
      await db.collection('notifications').add({ userId: user.id, profileId: user.id, title: 'Your week in review', body, type: 'weekly_digest', status: 'scheduled', scheduledFor, createdAt: FieldValue.serverTimestamp() });
      summary.digestsQueued++;
    });

    if (options.budgetMs != null && clock() - startedAt > options.budgetMs) { summary.stoppedEarly = page.size === pageSize; break; }
    if (page.size < pageSize) break;
  }
  return summary;
}
