import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore, Timestamp } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import * as functions from 'firebase-functions/v1';
import { createHash, randomBytes } from 'node:crypto';

initializeApp();
const db = getFirestore();
const region = 'asia-south1';

// createStaffAccount (below) creates the Auth user and writes its role to
// Firestore in the same request - but this trigger fires off the exact same
// Auth-user-created event, with no ordering guarantee against that write. A
// plain unconditional `.set({ role: 'user' })` here would silently stomp an
// admin/coach role back to 'user' if this trigger happened to run second.
// The transaction makes it order-independent: whichever of the two writes
// lands second sees the first one's role already there and preserves it.
export const onUserCreate = functions.region(region).auth.user().onCreate(async user => {
  const ref = db.doc(`users/${user.uid}`);
  await db.runTransaction(async tx => {
    const existing = await tx.get(ref);
    const role = existing.exists ? (existing.data()?.role ?? 'user') : 'user';
    const createdAt = existing.exists ? (existing.data()?.createdAt ?? FieldValue.serverTimestamp()) : FieldValue.serverTimestamp();
    tx.set(ref, { userId: user.uid, phone: user.phoneNumber ?? null, email: user.email ?? null, role, status: 'active', timezone: 'Asia/Kolkata', notificationPreferences: { quietHoursStart: '21:00', quietHoursEnd: '07:00', maxHealthReminders: 3 }, createdAt, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  });
  await consumeMatchingInvite(user.uid, user.email ?? null, user.phoneNumber ?? null);
});

// Auto-enrolls a brand-new account into whatever program staff pre-invited
// their email or phone to (see inviteToProgram below), so onboarding a known
// member never requires handing out a code at all - the moment they sign up
// with the invited contact, by whichever method (phone OTP or email), they
// land in the program automatically. Checks both contact channels since a
// person might sign up with either one. Deliberately keyed by the doc id
// (not a query) so this never scans the whole collection on every signup.
async function consumeMatchingInvite(uid: string, email: string | null, phone: string | null) {
  const candidates: string[] = [];
  if (email) candidates.push(`email_${email.trim().toLowerCase()}`);
  if (phone) candidates.push(`phone_${phone.trim()}`);
  for (const id of candidates) {
    const inviteRef = db.doc(`programInvites/${id}`);
    const invite = await inviteRef.get();
    if (!invite.exists || invite.get('consumedAt') != null) continue;
    const programId = String(invite.get('programId') ?? '');
    const programDoc = await db.doc(`programs/${programId}`).get();
    if (!programDoc.exists) continue;
    const programName = String(programDoc.get('name') ?? invite.get('programName') ?? 'Nirog Bhumi Program');
    const durationDays = programDurationDays(programDoc);
    const userDoc = await db.doc(`users/${uid}`).get();
    const memberName = String(userDoc.get('fullName') ?? '').trim() || 'Member';
    const batch = db.batch();
    batch.set(db.doc(`users/${uid}`), {
      programActive: true,
      activeProgramId: programId,
      activeProgramName: programName,
      programDurationDays: durationDays,
      programStartedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    batch.set(db.doc(`programMembers/${programId}_${uid}`), {
      programId, uid, name: memberName, status: 'active', joinedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    batch.set(inviteRef, { consumedAt: FieldValue.serverTimestamp(), consumedByUid: uid }, { merge: true });
    await batch.commit();
    return; // one matching invite is enough
  }
}

// The Android app only ever writes lastCheckinAt/checkinStreak onto
// users/{uid} (HealthRepository.recordCheckinCompletion) - but the admin
// console's roster pages (Dashboard, Members, Batches, MemberDetail) read
// consistency/quiet-member state off programMembers docs, which never had
// this field. Mirror it server-side on every check-in so the console's
// existing realtime programMembers listeners pick it up with no client
// changes, instead of every roster page having to join two collections.
export const onUserCheckinMirror = onDocumentWritten({ document: 'users/{uid}', region }, async event => {
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();
  if (!after) return;
  const checkinChanged = (before?.lastCheckinAt?.toMillis?.() ?? null) !== (after.lastCheckinAt?.toMillis?.() ?? null);
  // A pre-invited member is auto-enrolled at signup, before they've entered
  // their name - the roster doc starts as "Member" until their profile
  // saves fullName. Mirror that first real name in too, same trigger.
  const nameChanged = (before?.fullName ?? null) !== (after.fullName ?? null) && !!after.fullName;
  if (!checkinChanged && !nameChanged) return;
  const roster = await db.collection('programMembers').where('uid', '==', event.params.uid).get();
  if (roster.empty) return;
  const update: Record<string, unknown> = {};
  if (checkinChanged) { update.lastCheckinAt = after.lastCheckinAt; update.checkinStreak = after.checkinStreak ?? null; }
  if (nameChanged) update.name = after.fullName;
  const batch = db.batch();
  roster.docs.forEach(d => batch.set(d.ref, update, { merge: true }));
  await batch.commit();
});

function glucoseStatus(value: number, type: string) {
  // A missing/non-numeric value must never resolve to the reassuring
  // default - every comparison against NaN is false, so without this guard
  // a malformed reading would silently read as 'in_range' and skip the
  // critical-alert path entirely.
  if (!Number.isFinite(value)) return 'invalid';
  if (value >= 300 || value <= 54) return 'critical';
  if ((type === 'fasting' && value > 125) || (type !== 'fasting' && value > 180)) return 'needs_attention';
  return 'in_range';
}

function dayKeyIST(date = new Date()): string {
  // en-CA formats as YYYY-MM-DD, which is exactly the sortable key we want.
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

// Batch Pulse (PRD v2, Care+): a PII-free "N of M checked in today" count for
// the member's program. Deliberately does NOT expose which members checked
// in - only an aggregate - via a functions-only marker subcollection that
// dedupes a member logging multiple readings the same day into one count.
async function recordBatchCheckin(uid: string) {
  const user = await db.doc(`users/${uid}`).get();
  if (!user.exists || user.get('programActive') !== true) return;
  const programId = String(user.get('activeProgramId') ?? ''); if (!programId) return;
  const day = dayKeyIST();
  const statsRef = db.doc(`batchStats/${programId}_${day}`);
  const markerRef = statsRef.collection('checkedInMembers').doc(uid);
  if ((await markerRef.get()).exists) return; // already counted today
  const memberCount = (await db.collection('programMembers').where('programId', '==', programId).count().get()).data().count;
  await db.runTransaction(async tx => {
    const marker = await tx.get(markerRef);
    if (marker.exists) return;
    tx.set(markerRef, { markedAt: FieldValue.serverTimestamp() });
    tx.set(statsRef, { programId, dayKey: day, checkedInCount: FieldValue.increment(1), memberCount, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  });
}

export const onGlucoseReadingCreate = onDocumentCreated({ document: 'glucoseReadings/{readingId}', region }, async event => {
  const snap = event.data; if (!snap) return;
  const data = snap.data(); const value = Number(data.value); const status = glucoseStatus(value, String(data.readingType));
  await snap.ref.set({ status, categorizedAt: FieldValue.serverTimestamp() }, { merge: true });
  // Don't overwrite the user's last-known-good reading with a NaN from a
  // malformed entry - only mirror it forward once it's actually a number.
  if (Number.isFinite(value)) {
    await db.doc(`users/${data.userId}`).set({ latestMetrics: { fastingSugar: value, glucoseStatus: status, glucoseUpdatedAt: FieldValue.serverTimestamp() }, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
  // Critical alerts use their own notification type so sendPendingNotifications()
  // never defers them for quiet hours or the daily reminder cap - see there.
  if (status === 'critical') await db.collection('notifications').add({ userId: data.userId, profileId: data.profileId, title: 'Please review this reading', body: 'Repeat the measurement and contact your doctor promptly, especially if you feel unwell.', type: 'critical_alert', status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
  await recordBatchCheckin(String(data.userId));
});

export const onBPReadingCreate = onDocumentCreated({ document: 'bpReadings/{readingId}', region }, async event => {
  const snap = event.data; if (!snap) return; const d = snap.data();
  const systolic = Number(d.systolic); const diastolic = Number(d.diastolic);
  const valid = Number.isFinite(systolic) && Number.isFinite(diastolic);
  const critical = valid && (systolic >= 180 || diastolic >= 120);
  await snap.ref.set({ status: !valid ? 'invalid' : critical ? 'critical' : 'recorded', categorizedAt: FieldValue.serverTimestamp() }, { merge: true });
  if (critical) await db.collection('notifications').add({ userId: d.userId, profileId: d.profileId, title: 'Please review your BP reading', body: 'Repeat the measurement and seek urgent medical advice, especially if you feel unwell.', type: 'critical_alert', status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
  await recordBatchCheckin(String(d.userId));
});

// Announcement creation/fan-out/deletion is handled by the createAnnouncement/
// deleteAnnouncement callables further down (they need the Admin SDK to
// resolve audiences that cross program boundaries - all users, all enrolled,
// inactive, non-enrolled - which a Firestore trigger keyed on a single
// programId field can't express). See resolveAudienceUids and
// deleteAnnouncementDoc.

// Daily maintenance job. Runs once a day; on Mondays it also builds weekly
// reports. Keeping daily + weekly in one schedule keeps us to 3 Cloud
// Scheduler jobs total (this + notifications + deletions), inside the free tier.
export const generateDailyContent = onSchedule({ schedule: '0 5 * * *', timeZone: 'Asia/Kolkata', region }, async () => {
  const users = await db.collection('users').where('status', '==', 'active').get();
  const day = new Date().toISOString().slice(0, 10); const batch = db.batch();
  users.docs.forEach(user => batch.set(db.doc(`dailyActions/${user.id}_${day}`), { userId: user.id, profileId: user.id, dateKey: day, title: 'Walk 15 minutes after dinner', reason: 'A short post-meal walk can support your health rhythm.', status: 'pending', createdAt: FieldValue.serverTimestamp() }, { merge: true }));
  await batch.commit();

  // Batch Pulse collective goal: minutes walked together this month, per
  // active program. Runs daily (not per-log) since a monthly total doesn't
  // need intraday freshness, keeping this inside the existing job budget.
  const programsSnap = await db.collection('programs').get();
  const monthStart = new Date(); monthStart.setDate(1); monthStart.setHours(0, 0, 0, 0);
  const monthStartTs = Timestamp.fromDate(monthStart);
  const today = dayKeyIST();
  for (const programDoc of programsSnap.docs) {
    const programId = programDoc.id;
    const membersSnap = await db.collection('programMembers').where('programId', '==', programId).get();
    const uids = membersSnap.docs.map(m => String(m.get('uid') ?? '')).filter(Boolean);
    if (!uids.length) continue;
    let totalMinutes = 0;
    // Per-uid breakdown alongside the existing team total - only ever
    // surfaced client-side for members who've opted in (below), never a
    // silent default-on ranking.
    const minutesByUid: Record<string, number> = {};
    for (let i = 0; i < uids.length; i += 30) {
      const chunk = uids.slice(i, i + 30);
      const walks = await db.collection('walkLogs').where('userId', 'in', chunk).where('createdAt', '>=', monthStartTs).get();
      walks.docs.forEach(w => {
        const minutes = Number(w.get('minutes') ?? 0);
        totalMinutes += minutes;
        const uid = String(w.get('userId') ?? '');
        if (uid) minutesByUid[uid] = (minutesByUid[uid] ?? 0) + minutes;
      });
    }
    const leaderboard = membersSnap.docs
      .filter(m => m.get('leaderboardOptIn') === true)
      .map(m => ({ name: String(m.get('name') ?? 'Member'), minutes: Math.round(minutesByUid[String(m.get('uid') ?? '')] ?? 0) }))
      .sort((a, b) => b.minutes - a.minutes)
      .slice(0, 10);
    await db.doc(`batchStats/${programId}_${today}`).set({ programId, dayKey: today, collectiveMinutes: Math.round(totalMinutes), memberCount: uids.length, leaderboard, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }

  const weekdayIST = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Kolkata', weekday: 'short' }).format(new Date());
  if (weekdayIST !== 'Mon') return;
  const end = Timestamp.now(); const start = Timestamp.fromMillis(end.toMillis() - 7 * 86400000);
  // Digest notifications are scheduled a few hours out (not sent immediately
  // at this 5am run) so they land at a considerate mid-morning hour instead
  // of during most users' default quiet hours (21:00-07:00).
  const digestScheduledFor = Timestamp.fromMillis(Date.now() + 4 * 60 * 60000);
  for (const user of users.docs) {
    const [glucose, sleep] = await Promise.all([
      db.collection('glucoseReadings').where('userId', '==', user.id).where('measuredAt', '>=', start).get(),
      db.collection('sleepLogs').where('userId', '==', user.id).where('createdAt', '>=', start).get(),
    ]);
    const values = glucose.docs.map(x => Number(x.data().value)).filter(Number.isFinite);
    const average = values.length ? Math.round(values.reduce((a,b) => a+b, 0) / values.length) : null;
    await db.collection('weeklyReports').add({ userId: user.id, profileId: user.id, periodStart: start, periodEnd: end, glucoseAverage: average, glucoseLogCount: values.length, consistency: values.length >= 4 ? 'good' : 'building', recommendation: 'Focus on one consistent daily action this week.', createdAt: FieldValue.serverTimestamp() });

    // Weekly logging-coverage digest: same "days with a reading/sleep log
    // out of 7" coverage math the Insights screen already shows the member,
    // sent as a nudge rather than left for them to discover on their own.
    const loggedDayKeys = new Set<string>();
    for (const doc of [...glucose.docs, ...sleep.docs]) {
      const ts = (doc.get('measuredAt') ?? doc.get('createdAt')) as FirebaseFirestore.Timestamp | undefined;
      if (ts) loggedDayKeys.add(dayKeyIST(ts.toDate()));
    }
    const daysLogged = loggedDayKeys.size;
    if (daysLogged === 0) continue; // Never nag a fully inactive user with a "0/7" ping.
    const body = daysLogged >= 4
      ? `Great rhythm - you logged health data on ${daysLogged} of the last 7 days. Keep it up!`
      : `You logged health data on ${daysLogged} of the last 7 days. Try logging one thing today to build your rhythm.`;
    await db.collection('notifications').add({
      userId: user.id, profileId: user.id, title: 'Your week in review', body,
      type: 'weekly_digest', status: 'scheduled', scheduledFor: digestScheduledFor, createdAt: FieldValue.serverTimestamp(),
    });
  }
});

export const sendPendingNotifications = onSchedule({ schedule: 'every 15 minutes', timeZone: 'Asia/Kolkata', region }, async () => {
  const due = await db.collection('notifications').where('status', '==', 'scheduled').where('scheduledFor', '<=', Timestamp.now()).limit(100).get();
  for (const doc of due.docs) {
    const notification = doc.data(); const user = await db.doc(`users/${notification.userId}`).get();
    const preferences = user.get('notificationPreferences') ?? {}; const timezone = String(user.get('timezone') ?? 'Asia/Kolkata');
    const parts = new Intl.DateTimeFormat('en-GB', { timeZone: timezone, hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date());
    const currentMinutes = Number(parts.find(part => part.type === 'hour')?.value ?? 0) * 60 + Number(parts.find(part => part.type === 'minute')?.value ?? 0);
    const toMinutes = (value: unknown, fallback: number) => { const match = String(value ?? '').match(/^(\d{1,2}):(\d{2})$/); return match ? Number(match[1]) * 60 + Number(match[2]) : fallback; };
    const quietStart = toMinutes(preferences.quietHoursStart, 21 * 60); const quietEnd = toMinutes(preferences.quietHoursEnd, 7 * 60);
    const inQuietHours = quietStart < quietEnd ? currentMinutes >= quietStart && currentMinutes < quietEnd : currentMinutes >= quietStart || currentMinutes < quietEnd;
    if (notification.type === 'reminder' && inQuietHours) { await doc.ref.set({ scheduledFor: Timestamp.fromMillis(Date.now() + 60 * 60000), deferredReason: 'quiet_hours', updatedAt: FieldValue.serverTimestamp() }, { merge: true }); continue; }
    if (notification.type === 'reminder') {
      const startOfWindow = Timestamp.fromMillis(Date.now() - 24 * 60 * 60000); const sent = await db.collection('notifications').where('userId', '==', notification.userId).where('status', '==', 'sent').where('sentAt', '>=', startOfWindow).get();
      const maxReminders = Math.max(0, Math.min(5, Number(preferences.maxHealthReminders ?? 3)));
      if (sent.size >= maxReminders) { await doc.ref.set({ scheduledFor: Timestamp.fromMillis(Date.now() + 12 * 60 * 60000), deferredReason: 'daily_cap', updatedAt: FieldValue.serverTimestamp() }, { merge: true }); continue; }
    }
    const token = user.get('fcmToken');
    if (!token) { await doc.ref.set({ status: 'failed', failureReason: 'missing_token' }, { merge: true }); continue; }
    try {
      await getMessaging().send({ token, notification: { title: notification.title, body: notification.body }, data: { type: String(notification.type ?? 'reminder'), notificationId: doc.id } });
      await doc.ref.set({ status: 'sent', sentAt: FieldValue.serverTimestamp() }, { merge: true });
    } catch (error) { console.error('FCM send failed', doc.id, error); await doc.ref.set({ status: 'failed', failureReason: 'send_failed', updatedAt: FieldValue.serverTimestamp() }, { merge: true }); }
  }

  // Piggybacks the 24-hour-default announcement expiry cleanup onto this
  // existing 15-minute schedule rather than adding a 4th Cloud Scheduler job
  // (see the "3 jobs total" note on generateDailyContent) - deletes the
  // parent doc plus every recipient's fan-out copy under
  // users/{uid}/announcements, same as a manual deleteAnnouncement call.
  const expired = await db.collection('announcements').where('expiresAt', '<=', Timestamp.now()).limit(20).get();
  for (const doc of expired.docs) await deleteAnnouncementDoc(doc);
});

function requireUser(request: { auth?: { uid: string; token: Record<string, unknown> } }) { if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in required'); return request.auth; }

type AnnouncementAudienceScope = 'program' | 'all_enrolled' | 'all_users' | 'inactive' | 'non_enrolled';

interface AnnouncementAudience {
  scope: AnnouncementAudienceScope;
  programIds: string[];
  inactiveDays: number;
}

// Above this, a single onCall/onSchedule invocation risks running long enough
// (or writing enough batches) to be a bad idea in one shot - narrow the
// audience instead. Comfortably covers this app's actual scale today while
// keeping resolveAudienceUids/createAnnouncement bounded and predictable.
const MAX_ANNOUNCEMENT_RECIPIENTS = 5000;

// Resolves which uids a given audience selection actually reaches, and is
// also the authorization boundary for *which* audiences someone may target:
// 'program' is available to a coach too (but only for program(s) they're the
// assigned coachId of - never an arbitrary list); every scope that crosses
// program boundaries (all users, all enrolled, inactive, non-enrolled) is
// admin-only, mirroring the programStaff()/admin() split used everywhere
// else in this file.
async function resolveAudienceUids(audience: AnnouncementAudience, callerRole: unknown, callerUid: string): Promise<string[]> {
  const isAdmin = callerRole === 'admin' || callerRole === 'super_admin';
  if (audience.scope !== 'program' && !isAdmin) throw new HttpsError('permission-denied', 'Only an admin can target this audience');

  if (audience.scope === 'program') {
    const programIds = Array.from(new Set(audience.programIds.filter(Boolean)));
    if (!programIds.length) throw new HttpsError('invalid-argument', 'Select at least one program/batch');
    if (!isAdmin) {
      for (const id of programIds) {
        const program = await db.doc(`programs/${id}`).get();
        if (program.get('coachId') !== callerUid) throw new HttpsError('permission-denied', 'Not your program');
      }
    }
    const uids = new Set<string>();
    for (let i = 0; i < programIds.length; i += 10) {
      const chunk = programIds.slice(i, i + 10);
      const snap = await db.collection('programMembers').where('programId', 'in', chunk).get();
      snap.docs.forEach(d => { const uid = String(d.get('uid') ?? ''); if (uid) uids.add(uid); });
    }
    return Array.from(uids);
  }

  if (audience.scope === 'all_enrolled') {
    const snap = await db.collection('users').where('programActive', '==', true).limit(MAX_ANNOUNCEMENT_RECIPIENTS + 1).get();
    if (snap.size > MAX_ANNOUNCEMENT_RECIPIENTS) throw new HttpsError('resource-exhausted', `Audience too large for one send (max ${MAX_ANNOUNCEMENT_RECIPIENTS}) - narrow it`);
    return snap.docs.map(d => d.id);
  }

  // 'all_users', 'inactive', and 'non_enrolled' all need to see every user
  // doc (a plain where('lastCheckinAt','&lt;=',cutoff) would silently skip
  // anyone who's *never* checked in at all, since Firestore range filters
  // exclude documents missing the field - and "never checked in" is exactly
  // who an 'inactive' broadcast most needs to reach).
  const snap = await db.collection('users').limit(MAX_ANNOUNCEMENT_RECIPIENTS + 1).get();
  if (snap.size > MAX_ANNOUNCEMENT_RECIPIENTS) throw new HttpsError('resource-exhausted', `Audience too large for one send (max ${MAX_ANNOUNCEMENT_RECIPIENTS}) - narrow it`);
  if (audience.scope === 'all_users') return snap.docs.map(d => d.id);
  if (audience.scope === 'non_enrolled') return snap.docs.filter(d => d.get('programActive') !== true).map(d => d.id);
  const days = Math.max(1, Math.min(365, Math.round(audience.inactiveDays) || 4));
  const cutoffMillis = Date.now() - days * 86400000;
  return snap.docs.filter(d => { const ts = d.get('lastCheckinAt'); return !ts || ts.toMillis() <= cutoffMillis; }).map(d => d.id);
}

// The one place recipient fan-out copies get deleted, shared by the manual
// deleteAnnouncement callable and the 24h-default expiry sweep inside
// sendPendingNotifications - both need to remove the same set of documents,
// so this is the single source of truth for what "delete an announcement"
// actually touches.
async function deleteAnnouncementDoc(snap: FirebaseFirestore.DocumentSnapshot) {
  const recipients: string[] = Array.isArray(snap.get('recipientUids')) ? snap.get('recipientUids') : [];
  for (let offset = 0; offset < recipients.length; offset += 400) {
    const batch = db.batch();
    recipients.slice(offset, offset + 400).forEach(uid => batch.delete(db.doc(`users/${uid}/announcements/${snap.id}`)));
    await batch.commit();
  }
  await snap.ref.delete();
}

// Replaces the old direct-client-write + onAnnouncementCreate-trigger pair:
// audiences that cross program boundaries (all users, all enrolled,
// inactive, non-enrolled) need the Admin SDK to resolve, which a client
// write + Firestore trigger can't do - so composing now goes through this
// callable end to end (resolve recipients, write the source doc, fan out to
// every selected channel) instead of two separate code paths.
export const createAnnouncement = onCall({ region, timeoutSeconds: 120 }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  if (role !== 'admin' && role !== 'super_admin' && role !== 'coach') throw new HttpsError('permission-denied', 'Staff only');

  const title = String(request.data?.title ?? '').trim().slice(0, 120);
  const body = String(request.data?.body ?? '').trim().slice(0, 2000);
  if (!title || !body) throw new HttpsError('invalid-argument', 'Title and body are required');

  const rawAudience = (request.data?.audience ?? {}) as Record<string, unknown>;
  const scope = String(rawAudience.scope ?? 'program') as AnnouncementAudienceScope;
  if (!['program', 'all_enrolled', 'all_users', 'inactive', 'non_enrolled'].includes(scope)) throw new HttpsError('invalid-argument', 'Unknown audience scope');
  const audience: AnnouncementAudience = {
    scope,
    programIds: Array.isArray(rawAudience.programIds) ? rawAudience.programIds.map(String) : [],
    inactiveDays: Number(rawAudience.inactiveDays ?? 4),
  };

  const rawChannels = (request.data?.channels ?? {}) as Record<string, unknown>;
  const channels = { inApp: rawChannels.inApp !== false, push: rawChannels.push === true, email: rawChannels.email === true };
  if (!channels.inApp && !channels.push && !channels.email) throw new HttpsError('invalid-argument', 'Select at least one delivery channel');

  const expiresInHours = Math.max(1, Math.min(720, Math.round(Number(request.data?.expiresInHours ?? 24)) || 24));

  const recipients = await resolveAudienceUids(audience, role, auth.uid);
  if (!recipients.length) throw new HttpsError('invalid-argument', 'No members match this audience');

  const now = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(now.toMillis() + expiresInHours * 3600000);
  const authorDoc = await db.doc(`users/${auth.uid}`).get();
  const authorName = String(authorDoc.get('fullName') ?? 'Nirog Bhumi team');

  const announcementRef = db.collection('announcements').doc();
  await announcementRef.set({
    title, body, authorId: auth.uid, authorName, createdAt: FieldValue.serverTimestamp(), expiresAt,
    audience, channels, recipientUids: recipients, recipientCount: recipients.length, seenCount: 0,
  });

  if (channels.inApp) {
    for (let offset = 0; offset < recipients.length; offset += 400) {
      const batch = db.batch();
      recipients.slice(offset, offset + 400).forEach(uid => {
        batch.set(db.doc(`users/${uid}/announcements/${announcementRef.id}`), { announcementId: announcementRef.id, title, body, authorName, createdAt: now, expiresAt });
      });
      await batch.commit();
    }
  }

  if (channels.push) {
    for (let offset = 0; offset < recipients.length; offset += 400) {
      const batch = db.batch();
      recipients.slice(offset, offset + 400).forEach(uid => {
        if (uid === auth.uid) return; // the author doesn't need a push about their own post
        batch.set(db.collection('notifications').doc(), {
          userId: uid, profileId: null, title, body, type: 'announcement',
          status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp(),
        });
      });
      await batch.commit();
    }
  }

  if (channels.email) {
    // Writes into the `mail` collection shape the Firebase "Trigger Email"
    // extension expects. That extension isn't installed yet (needs an owner
    // action: install it + configure SMTP/SendGrid credentials, same
    // owner-gated-infra pattern as the org-policy IAM fix documented in
    // docs/deploy-wif-setup.md) - until then these docs are written but
    // nothing sends them. Honest partial functionality, not a fake success.
    for (let i = 0; i < recipients.length; i += 300) {
      const chunk = recipients.slice(i, i + 300);
      const userDocs = await db.getAll(...chunk.map(uid => db.doc(`users/${uid}`)));
      const batch = db.batch();
      userDocs.forEach(u => {
        const email = u.get('email');
        if (!email) return;
        batch.set(db.collection('mail').doc(), { to: [String(email)], message: { subject: title, text: body } });
      });
      await batch.commit();
    }
  }

  await db.collection('auditLogs').add({
    actorId: auth.uid, actorRole: role ?? 'coach', action: 'create_announcement', entityType: 'announcement', entityId: announcementRef.id,
    metadata: { scope: audience.scope, channels, recipientCount: recipients.length, title }, createdAt: FieldValue.serverTimestamp(),
  });

  return { announcementId: announcementRef.id, recipientCount: recipients.length };
});

// Lets the console show a live "This reaches N people" count before sending,
// without actually creating/fanning out anything - reuses the exact same
// resolver and authorization boundary as the real send.
export const previewAnnouncementAudience = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  if (role !== 'admin' && role !== 'super_admin' && role !== 'coach') throw new HttpsError('permission-denied', 'Staff only');
  const rawAudience = (request.data?.audience ?? {}) as Record<string, unknown>;
  const scope = String(rawAudience.scope ?? 'program') as AnnouncementAudienceScope;
  const audience: AnnouncementAudience = {
    scope,
    programIds: Array.isArray(rawAudience.programIds) ? rawAudience.programIds.map(String) : [],
    inactiveDays: Number(rawAudience.inactiveDays ?? 4),
  };
  const recipients = await resolveAudienceUids(audience, role, auth.uid);
  return { count: recipients.length };
});

// Manual early delete (the 24h default expiry above handles the rest on its
// own). A coach may only remove their own post; admin/super_admin may remove
// any announcement.
export const deleteAnnouncement = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  const id = String(request.data?.id ?? '');
  if (!id) throw new HttpsError('invalid-argument', 'id is required');
  const ref = db.doc(`announcements/${id}`);
  const snap = await ref.get();
  if (!snap.exists) return { deleted: false };

  const isAdmin = role === 'admin' || role === 'super_admin';
  if (!isAdmin && (role !== 'coach' || snap.get('authorId') !== auth.uid)) {
    throw new HttpsError('permission-denied', 'You can only delete your own announcements');
  }

  await deleteAnnouncementDoc(snap);
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: role ?? 'coach', action: 'delete_announcement', entityType: 'announcement', entityId: id, createdAt: FieldValue.serverTimestamp() });
  return { deleted: true };
});

// "Seen by N" for a coach, without a per-message read-receipt list on the
// member-facing fan-out doc (users/{uid}/announcements/{id}) - a small
// marker subcollection under the master doc dedups repeat views from the
// same member, mirroring the exact pattern batchStats/{id}/checkedInMembers
// already uses for the same reason (increment once per uid, not once per
// view). Never throws on a bad/expired id - marking something as "seen"
// that no longer exists shouldn't surface an error to the member's UI.
export const markAnnouncementSeen = onCall({ region }, async request => {
  const auth = requireUser(request);
  const announcementId = String(request.data?.announcementId ?? '');
  if (!announcementId) throw new HttpsError('invalid-argument', 'announcementId is required');
  const announcementRef = db.doc(`announcements/${announcementId}`);
  const markerRef = announcementRef.collection('seenMarkers').doc(auth.uid);
  await db.runTransaction(async tx => {
    const [announcementSnap, markerSnap] = await Promise.all([tx.get(announcementRef), tx.get(markerRef)]);
    if (!announcementSnap.exists || markerSnap.exists) return;
    tx.set(markerRef, { seenAt: FieldValue.serverTimestamp() });
    tx.update(announcementRef, { seenCount: FieldValue.increment(1) });
  });
  return { ok: true };
});

// The console's program editor (Programs.tsx) only ever sets durationWeeks -
// durationDays has never actually been written by anything, so reading it
// directly always silently resolved to 0 and every enrolled member's
// programDurationDays came out 0 (shows as "Day N" with no total instead of
// "Day N of 42" in the app). Prefer a legacy durationDays if one's ever set
// by something else, otherwise derive it from the field that's actually populated.
function programDurationDays(programDoc: FirebaseFirestore.DocumentSnapshot): number {
  const rawDays = programDoc.get('durationDays');
  if (rawDays != null) return Math.round(Number(rawDays)) || 0;
  const weeks = Number(programDoc.get('durationWeeks') ?? 0);
  return Math.round(weeks * 7) || 0;
}

// Redeems a Care+ program invite code. This used to be a client-side
// Firestore query + self-scoped write (programs.code was publicly readable,
// and users/{uid}.programActive/activeProgramId were owner-writable) - that
// let any signed-in user list every program's code and self-enroll without
// ever redeeming one, and forge their own programMembers roster stats. Now
// the lookup and both writes happen here, under the Admin SDK, so
// firestore.rules can lock 'programs' reads to staff() and make
// 'programMembers' writes staff()-only.
export const redeemProgramCode = onCall({ region }, async request => {
  const auth = requireUser(request);
  const code = String(request.data?.code ?? '').trim().toUpperCase();
  if (!code) throw new HttpsError('invalid-argument', 'A program code is required');

  const matches = await db.collection('programs').where('code', '==', code).limit(1).get();
  const programDoc = matches.docs[0];
  if (!programDoc) throw new HttpsError('not-found', "That program code wasn't recognized");

  const programId = programDoc.id;
  const programName = String(programDoc.get('name') ?? 'Nirog Bhumi Program');
  const durationDays = programDurationDays(programDoc);

  const userDoc = await db.doc(`users/${auth.uid}`).get();
  const memberName = String(userDoc.get('fullName') ?? '').trim() || 'Member';

  // Enrollment and the roster entry the coach console reads from must land
  // together, or the console shows a phantom program with no members.
  const batch = db.batch();
  batch.set(db.doc(`users/${auth.uid}`), {
    programActive: true,
    activeProgramId: programId,
    activeProgramName: programName,
    programDurationDays: durationDays,
    programStartedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  batch.set(db.doc(`programMembers/${programId}_${auth.uid}`), {
    programId, uid: auth.uid, name: memberName, status: 'active', joinedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await batch.commit();

  return { activeProgramId: programId, activeProgramName: programName, programDurationDays: durationDays, programActive: true };
});

// Self-heals a missing programMembers roster doc for an account that Firestore
// itself already considers an active program member (users/{uid}.programActive
// == true) but whose roster entry is somehow absent - e.g. an account enrolled
// under an older, pre-hardening version of redeemProgramCode that wrote these
// as two separate non-atomic writes, where the first could succeed and the
// second silently never run. Deliberately does NOT accept a programId from the
// client and only ever acts on the caller's own already-authoritative
// programActive/activeProgramId - it repairs an existing enrollment, it can
// never grant a new one (that's still exclusively redeemProgramCode's job).
export const ensureProgramMembership = onCall({ region }, async request => {
  const auth = requireUser(request);
  const userDoc = await db.doc(`users/${auth.uid}`).get();
  if (userDoc.get('programActive') !== true) throw new HttpsError('failed-precondition', 'No active program on this account');
  const programId = String(userDoc.get('activeProgramId') ?? '');
  if (!programId) throw new HttpsError('failed-precondition', 'No active program on this account');
  const memberRef = db.doc(`programMembers/${programId}_${auth.uid}`);
  if ((await memberRef.get()).exists) return { repaired: false, programId };
  const memberName = String(userDoc.get('fullName') ?? '').trim() || 'Member';
  await memberRef.set({ programId, uid: auth.uid, name: memberName, status: 'active', joinedAt: FieldValue.serverTimestamp() }, { merge: true });
  return { repaired: true, programId };
});

// Console-side manual enrollment - the same underlying write redeemProgramCode
// does, but triggered by staff picking an existing user + program from the
// Users & Roles page, for the case a member can't redeem their own code
// (the "unauthenticated" enrollment bug some accounts have hit, or simply
// someone who never got a code). Same staff/own-program authorization
// boundary as sendBulkNotification: admin/super_admin can enroll into any
// program, a coach only into a program they're assigned to.
export const adminEnrollUser = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  const uid = String(request.data?.uid ?? '');
  const programId = String(request.data?.programId ?? '');
  if (!uid || !programId) throw new HttpsError('invalid-argument', 'A uid and programId are required');

  const programDoc = await db.doc(`programs/${programId}`).get();
  if (!programDoc.exists) throw new HttpsError('not-found', "That program wasn't found");

  if (role !== 'admin' && role !== 'super_admin') {
    if (role !== 'coach') throw new HttpsError('permission-denied', 'Staff only');
    if (programDoc.get('coachId') !== auth.uid) throw new HttpsError('permission-denied', 'Not your program');
  }

  const targetUser = await db.doc(`users/${uid}`).get();
  if (!targetUser.exists) throw new HttpsError('not-found', "That user wasn't found");

  const programName = String(programDoc.get('name') ?? 'Nirog Bhumi Program');
  const durationDays = programDurationDays(programDoc);
  const memberName = String(targetUser.get('fullName') ?? '').trim() || 'Member';

  const batch = db.batch();
  batch.set(db.doc(`users/${uid}`), {
    programActive: true,
    activeProgramId: programId,
    activeProgramName: programName,
    programDurationDays: durationDays,
    programStartedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  batch.set(db.doc(`programMembers/${programId}_${uid}`), {
    programId, uid, name: memberName, status: 'active', joinedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  await batch.commit();

  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: role ?? 'admin', action: 'admin_enroll_user', entityType: 'user', entityId: uid, metadata: { programId, programName }, createdAt: FieldValue.serverTimestamp() });
  return { activeProgramId: programId, activeProgramName: programName, programDurationDays: durationDays };
});

function normalizeContact(raw: string): { type: 'email' | 'phone'; key: string } | null {
  const trimmed = raw.trim();
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return { type: 'email', key: trimmed.toLowerCase() };
  if (/^\+[1-9]\d{7,14}$/.test(trimmed)) return { type: 'phone', key: trimmed };
  return null;
}

// Pre-enrolls someone who hasn't signed up yet - staff enters the phone
// number or email a person will use, and the moment an account with that
// exact contact is created (onUserCreate -> consumeMatchingInvite above),
// they land in the program automatically. No code to hand out, no manual
// enroll step after the fact - this is the "onboard by contact" flow instead
// of the self-serve program-code flow. Same staff/own-program boundary as
// adminEnrollUser/sendBulkNotification.
export const inviteToProgram = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  const programId = String(request.data?.programId ?? '');
  const contactRaw = String(request.data?.contact ?? '');
  if (!programId || !contactRaw) throw new HttpsError('invalid-argument', 'A contact (email or phone) and programId are required');
  const contact = normalizeContact(contactRaw);
  if (!contact) throw new HttpsError('invalid-argument', 'Enter a valid email, or a phone number with country code (e.g. +919876543210)');

  const programDoc = await db.doc(`programs/${programId}`).get();
  if (!programDoc.exists) throw new HttpsError('not-found', "That program wasn't found");
  if (role !== 'admin' && role !== 'super_admin') {
    if (role !== 'coach') throw new HttpsError('permission-denied', 'Staff only');
    if (programDoc.get('coachId') !== auth.uid) throw new HttpsError('permission-denied', 'Not your program');
  }

  const programName = String(programDoc.get('name') ?? 'Nirog Bhumi Program');
  const inviteId = `${contact.type}_${contact.key}`;
  await db.doc(`programInvites/${inviteId}`).set({
    contact: contact.key, contactType: contact.type, programId, programName,
    invitedBy: auth.uid, invitedByRole: role, createdAt: FieldValue.serverTimestamp(),
    consumedAt: null, consumedByUid: null,
  });
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: role ?? 'admin', action: 'invite_to_program', entityType: 'programInvite', entityId: inviteId, metadata: { contact: contact.key, programId, programName }, createdAt: FieldValue.serverTimestamp() });
  return { id: inviteId, contact: contact.key, contactType: contact.type, programId, programName };
});

export const revokeInvite = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  const id = String(request.data?.id ?? '');
  if (!id) throw new HttpsError('invalid-argument', 'An invite id is required');
  const ref = db.doc(`programInvites/${id}`);
  const invite = await ref.get();
  if (!invite.exists) return { revoked: false };
  if (role !== 'admin' && role !== 'super_admin') {
    if (role !== 'coach') throw new HttpsError('permission-denied', 'Staff only');
    const programDoc = await db.doc(`programs/${invite.get('programId')}`).get();
    if (programDoc.get('coachId') !== auth.uid) throw new HttpsError('permission-denied', 'Not your program');
  }
  await ref.delete();
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: role ?? 'admin', action: 'revoke_invite', entityType: 'programInvite', entityId: id, createdAt: FieldValue.serverTimestamp() });
  return { revoked: true };
});

// CSV bulk import from the console (Programs page's global import, and
// Batches page's per-batch shortcut) - one call per row would be slow and
// give confusing partial-failure UX for a spreadsheet of dozens/hundreds of
// contacts, so this processes the whole set server-side and returns a
// per-row outcome the console can render as an import summary. For each
// contact: if an Auth account already exists with that email/phone, enroll
// them immediately (the same write adminEnrollUser does); otherwise create
// a pending invite (the same write inviteToProgram does) that auto-consumes
// the moment they sign up. Same staff/own-program authorization boundary as
// adminEnrollUser/inviteToProgram, checked per row since a single CSV could
// in principle mix programs a coach doesn't manage.
export const bulkOnboard = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  const rows = Array.isArray(request.data?.rows) ? request.data.rows : [];
  if (!rows.length) throw new HttpsError('invalid-argument', 'At least one row is required');
  if (rows.length > 300) throw new HttpsError('invalid-argument', 'Too many rows in one import (max 300)');

  const results: { contact: string; status: 'enrolled' | 'invited' | 'error'; message?: string }[] = [];
  const programCache = new Map<string, FirebaseFirestore.DocumentSnapshot | null>();
  async function getProgramChecked(programId: string): Promise<FirebaseFirestore.DocumentSnapshot | null> {
    if (!programCache.has(programId)) {
      const programDoc = await db.doc(`programs/${programId}`).get();
      programCache.set(programId, programDoc.exists ? programDoc : null);
    }
    return programCache.get(programId) ?? null;
  }

  for (const row of rows as Array<Record<string, unknown>>) {
    const contactRaw = String(row?.contact ?? '');
    const programId = String(row?.programId ?? '');
    if (!contactRaw || !programId) { results.push({ contact: contactRaw || '(blank)', status: 'error', message: 'Missing contact or program' }); continue; }
    const contact = normalizeContact(contactRaw);
    if (!contact) { results.push({ contact: contactRaw, status: 'error', message: 'Invalid email or phone' }); continue; }

    const programDoc = await getProgramChecked(programId);
    if (!programDoc) { results.push({ contact: contact.key, status: 'error', message: 'Program not found' }); continue; }
    if (role !== 'admin' && role !== 'super_admin') {
      if (role !== 'coach' || programDoc.get('coachId') !== auth.uid) {
        results.push({ contact: contact.key, status: 'error', message: 'Not your program' });
        continue;
      }
    }

    try {
      const existingUser = contact.type === 'email'
        ? await getAuth().getUserByEmail(contact.key).catch(() => null)
        : await getAuth().getUserByPhoneNumber(contact.key).catch(() => null);

      const programName = String(programDoc.get('name') ?? 'Nirog Bhumi Program');
      const durationDays = programDurationDays(programDoc);

      if (existingUser) {
        const uid = existingUser.uid;
        const targetUser = await db.doc(`users/${uid}`).get();
        const memberName = String(targetUser.get('fullName') ?? '').trim() || 'Member';
        const batch = db.batch();
        batch.set(db.doc(`users/${uid}`), {
          programActive: true, activeProgramId: programId, activeProgramName: programName,
          programDurationDays: durationDays, programStartedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        batch.set(db.doc(`programMembers/${programId}_${uid}`), {
          programId, uid, name: memberName, status: 'active', joinedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
        await batch.commit();
        results.push({ contact: contact.key, status: 'enrolled' });
      } else {
        const inviteId = `${contact.type}_${contact.key}`;
        await db.doc(`programInvites/${inviteId}`).set({
          contact: contact.key, contactType: contact.type, programId, programName,
          invitedBy: auth.uid, invitedByRole: role, createdAt: FieldValue.serverTimestamp(),
          consumedAt: null, consumedByUid: null,
        });
        results.push({ contact: contact.key, status: 'invited' });
      }
    } catch (err) {
      results.push({ contact: contact.key, status: 'error', message: err instanceof Error ? err.message : 'Something went wrong' });
    }
  }

  await db.collection('auditLogs').add({
    actorId: auth.uid, actorRole: role ?? 'admin', action: 'bulk_onboard', entityType: 'programInvite', entityId: 'bulk',
    metadata: {
      count: rows.length,
      enrolled: results.filter(r => r.status === 'enrolled').length,
      invited: results.filter(r => r.status === 'invited').length,
      errors: results.filter(r => r.status === 'error').length,
    },
    createdAt: FieldValue.serverTimestamp(),
  });

  return { results };
});

export const requestDataExport = onCall({ region }, async request => {
  const auth = requireUser(request);
  // Each request triggers exportUserData, which reads ~19 collections and
  // writes a Storage file - unthrottled, a user could loop this call to
  // burn reads/writes/invocations at will. One export per hour is plenty
  // for the legitimate "download my data" use case.
  const cooldown = Timestamp.fromMillis(Date.now() - 60 * 60000);
  const recent = await db.collection('dataExportRequests').where('userId', '==', auth.uid).where('createdAt', '>=', cooldown).limit(1).get();
  if (!recent.empty) throw new HttpsError('resource-exhausted', 'You can request one export per hour - please try again later.');
  await db.collection('dataExportRequests').add({ userId: auth.uid, status: 'requested', createdAt: FieldValue.serverTimestamp() }); return { accepted: true };
});
export const requestAccountDeletion = onCall({ region }, async request => {
  const auth = requireUser(request);
  // Same cooldown as requestDataExport, for the same reason - one request is
  // plenty for the legitimate flow, and this can no longer be skipped via a
  // direct Firestore write (see the dataExportRequests/deletionRequests
  // create rule), but the callable itself still had no limit of its own.
  const cooldown = Timestamp.fromMillis(Date.now() - 60 * 60000);
  const recent = await db.collection('deletionRequests').where('userId', '==', auth.uid).where('createdAt', '>=', cooldown).limit(1).get();
  if (!recent.empty) throw new HttpsError('resource-exhausted', 'You can request this once per hour - please try again later.');
  await db.collection('deletionRequests').add({ userId: auth.uid, status: 'requested', createdAt: FieldValue.serverTimestamp() }); return { accepted: true };
});

export const exportUserData = onDocumentCreated({ document: 'dataExportRequests/{requestId}', region }, async event => {
  const request = event.data; if (!request) return; const uid = request.get('userId'); if (!uid) return;
  await request.ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  const names = ['users','profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections','medicationLogs'];
  const exported: Record<string, unknown> = { exportedAt: new Date().toISOString(), formatVersion: 1 };
  for (const name of names) {
    if (name === 'users') { const user = await db.doc(`users/${uid}`).get(); exported.users = user.exists ? [{ id: user.id, ...user.data() }] : []; continue; }
    const snapshot = await db.collection(name).where('userId', '==', uid).get(); exported[name] = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
  }
  const path = `users/${uid}/exports/${request.id}.json`; const file = getStorage().bucket().file(path);
  await file.save(JSON.stringify(exported, null, 2), { contentType: 'application/json', metadata: { cacheControl: 'private, max-age=0', metadata: { ownerUid: uid } } });
  await request.ref.set({ status: 'completed', storagePath: path, completedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await db.collection('notifications').add({ userId: uid, profileId: null, title: 'Your data export is ready', body: 'Open Privacy and Data Controls to access your export.', type: 'report', status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
});
// Real, time-limited signed URL for the Health File "share link / QR code"
// feature - the client previously used the Storage download-token URL
// directly (works, but never expires and isn't a true signed URL). Requires
// the Functions runtime service account to have `roles/iam.serviceAccountTokenCreator`
// bound to itself (a one-time `gcloud iam service-accounts add-iam-policy-binding`
// grant, same shape as the WIF setup in docs/deploy-wif-setup.md) - if that
// grant hasn't been done yet, this throws a clear 'failed-precondition' and
// the Android client falls back to the existing non-expiring link rather
// than breaking the feature.
export const getHealthFileShareLink = onCall({ region }, async request => {
  const auth = requireUser(request);
  const storagePath = String(request.data?.storagePath ?? '');
  // Owner-only: without this check, any signed-in user could pass another
  // member's Health File path and get a working link to their private data.
  if (!storagePath.startsWith(`users/${auth.uid}/health-file/`)) {
    throw new HttpsError('permission-denied', 'You can only share your own Health File');
  }
  try {
    const [url] = await getStorage().bucket().file(storagePath).getSignedUrl({
      version: 'v4',
      action: 'read',
      expires: Date.now() + 7 * 24 * 60 * 60 * 1000,
    });
    return { url, expiresInDays: 7 };
  } catch (error) {
    console.error('getHealthFileShareLink signing failed', error);
    throw new HttpsError('failed-precondition', 'Signed links are not set up yet - showing a standard link instead.');
  }
});

export const createAuditLog = onCall({ region }, async request => {
  const auth = requireUser(request);
  if (auth.token.role !== 'admin' && auth.token.role !== 'super_admin') throw new HttpsError('permission-denied', 'Admin only');
  const data = request.data as Record<string, unknown>;
  const action = String(data.action ?? '').slice(0, 100);
  const entityType = String(data.entityType ?? '').slice(0, 100);
  const entityId = String(data.entityId ?? '').slice(0, 200);
  if (!action || !entityType || !entityId) throw new HttpsError('invalid-argument', 'action, entityType and entityId are required');
  const metadata = data.metadata && typeof data.metadata === 'object' ? data.metadata : {};
  if (JSON.stringify(metadata).length > 4000) throw new HttpsError('invalid-argument', 'metadata is too large');
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: auth.token.role, action, entityType, entityId, metadata, createdAt: FieldValue.serverTimestamp() });
  return { logged: true };
});
export const queueDeletionRequest = onDocumentCreated({ document: 'deletionRequests/{requestId}', region }, async event => {
  await event.data?.ref.set({ status: 'awaiting_verification', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
});

// Feature areas a coach's console access can be scoped to. Admin/super_admin
// always have full access regardless of this list (it only narrows coaches).
const PERMISSION_KEYS = ['moderation', 'batches', 'announcements', 'calendar', 'programs', 'consultations', 'support', 'members'] as const;
type PermissionKey = typeof PERMISSION_KEYS[number];

// Sets a user's role custom claim (used by the admin console's Users page).
// Only an admin/super_admin may call it; it updates both the Auth claim (the
// enforcement source) and the mirrored users/{uid}.role field, and audits it.
// For role 'coach' it also accepts a `permissions` array that scopes exactly
// which console feature areas that coach can use - stored in the same custom
// claim (`perms`) so it's part of the enforcement source, not just UI dressing.
export const setUserRole = onCall({ region }, async request => {
  const auth = requireUser(request);
  const callerRole = auth.token.role;
  if (callerRole !== 'admin' && callerRole !== 'super_admin') throw new HttpsError('permission-denied', 'Admin only');
  const uid = String(request.data?.uid ?? '');
  const role = String(request.data?.role ?? '');
  if (!uid || !['user', 'coach', 'admin', 'super_admin'].includes(role)) throw new HttpsError('invalid-argument', 'A uid and a valid role (user|coach|admin|super_admin) are required');
  if (uid === auth.uid && role !== 'admin' && role !== 'super_admin') throw new HttpsError('failed-precondition', 'You cannot remove your own admin role');

  // A plain admin and super_admin were previously equal in enforcement here,
  // meaning any one admin account (compromised or malicious) could mint or
  // demote unlimited other admins. Only super_admin may now grant the admin
  // or super_admin role, or change the role of an account that's already
  // admin/super_admin.
  if (callerRole !== 'super_admin') {
    if (role === 'admin' || role === 'super_admin') throw new HttpsError('permission-denied', 'Only a super admin can grant the admin or super admin role');
    const target = await getAuth().getUser(uid).catch(() => null);
    const targetRole = target?.customClaims?.role;
    if (targetRole === 'admin' || targetRole === 'super_admin') {
      throw new HttpsError('permission-denied', "Only a super admin can change another admin's role");
    }
  }

  let perms: PermissionKey[] | null = null;
  if (role === 'coach') {
    const requested = Array.isArray(request.data?.permissions) ? request.data.permissions : PERMISSION_KEYS;
    perms = requested.filter((p: unknown): p is PermissionKey => PERMISSION_KEYS.includes(p as PermissionKey));
  }

  const claims: Record<string, unknown> = { role };
  if (perms) claims.perms = perms;
  await getAuth().setCustomUserClaims(uid, claims);
  await db.doc(`users/${uid}`).set({ role, permissions: perms ?? FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: auth.token.role ?? 'admin', action: 'set_role', entityType: 'user', entityId: uid, metadata: { role, permissions: perms }, createdAt: FieldValue.serverTimestamp() });
  return { updated: true, uid, role, permissions: perms };
});

// Creates a brand-new staff (coach/admin) account from the console's Users
// page - previously the only way to add a new admin/coach was to create the
// Auth user by hand in the Firebase Console, then promote them via
// setUserRole. Same privilege tiering as setUserRole: only a super_admin may
// create an admin account; a plain admin may only create coach accounts.
// Returns a one-time temporary password (shown once in the console) rather
// than emailing an invite, since this project has no transactional email
// sender wired up - the admin relays it to the new hire directly, who can
// then use "Forgot password" to set their own.
export const createStaffAccount = onCall({ region }, async request => {
  const auth = requireUser(request);
  const callerRole = auth.token.role;
  if (callerRole !== 'admin' && callerRole !== 'super_admin') throw new HttpsError('permission-denied', 'Admin only');
  const email = String(request.data?.email ?? '').trim().toLowerCase();
  const role = String(request.data?.role ?? '');
  const name = String(request.data?.name ?? '').trim();
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new HttpsError('invalid-argument', 'A valid email is required');
  if (!['coach', 'admin', 'super_admin'].includes(role)) throw new HttpsError('invalid-argument', 'Role must be coach, admin, or super admin');
  if ((role === 'admin' || role === 'super_admin') && callerRole !== 'super_admin') throw new HttpsError('permission-denied', 'Only a super admin can create an admin or super admin account');

  const existing = await getAuth().getUserByEmail(email).catch(() => null);
  if (existing) throw new HttpsError('already-exists', 'An account with that email already exists - use the role control below instead of creating a new one');

  const tempPassword = randomBytes(9).toString('base64').replace(/[+/=]/g, '');
  const created = await getAuth().createUser({ email, password: tempPassword, displayName: name || undefined, emailVerified: false });

  const perms = role === 'coach' ? [...PERMISSION_KEYS] : null;
  const claims: Record<string, unknown> = { role };
  if (perms) claims.perms = perms;
  await getAuth().setCustomUserClaims(created.uid, claims);
  await db.doc(`users/${created.uid}`).set({ userId: created.uid, email, fullName: name || null, role, permissions: perms ?? FieldValue.delete(), status: 'active', createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: callerRole, action: 'create_staff_account', entityType: 'user', entityId: created.uid, metadata: { email, role }, createdAt: FieldValue.serverTimestamp() });
  return { uid: created.uid, email, role, tempPassword };
});

// One-time bootstrap for the very first super_admin. Every other path to
// super_admin (setUserRole, createStaffAccount) requires being called BY an
// existing super_admin - a deliberate chicken-and-egg gap before this project
// had one. This callable is the sole, narrow exception: it only ever
// promotes one hardcoded, pre-agreed account, and it permanently disables
// itself (via the system/superAdminBootstrap marker doc, written in the same
// transaction as the check) the first time it succeeds, so it can't be
// replayed to mint a second super_admin later.
const BOOTSTRAP_SUPER_ADMIN_EMAIL = 'priyanshu@nirogbhumi.com';

export const bootstrapSuperAdmin = onCall({ region }, async request => {
  const auth = requireUser(request);
  const email = String(auth.token.email ?? '').toLowerCase();
  if (email !== BOOTSTRAP_SUPER_ADMIN_EMAIL) {
    throw new HttpsError('permission-denied', 'This account is not eligible for the one-time super admin bootstrap');
  }

  const markerRef = db.doc('system/superAdminBootstrap');
  await db.runTransaction(async tx => {
    const marker = await tx.get(markerRef);
    if (marker.exists) throw new HttpsError('failed-precondition', 'Super admin has already been bootstrapped');
    tx.set(markerRef, { usedBy: auth.uid, usedByEmail: email, usedAt: FieldValue.serverTimestamp() });
  });

  await getAuth().setCustomUserClaims(auth.uid, { role: 'super_admin' });
  await db.doc(`users/${auth.uid}`).set({ role: 'super_admin', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: 'super_admin', action: 'bootstrap_super_admin', entityType: 'user', entityId: auth.uid, metadata: { email }, createdAt: FieldValue.serverTimestamp() });
  return { updated: true };
});

// Console's "message all quiet members" bulk action. A direct client write to
// notifications is deliberately blocked by firestore.rules for docs carrying
// status/scheduledFor/type (see the comment there) - it exists specifically
// so a coach can't spam the shared 15-minute send queue, or message someone
// outside their own program by forging userId. This callable is the one
// sanctioned way through that boundary: same programStaff() authorization
// boundary as the rest of the program-scoped console actions (admin, or the
// coach assigned to this exact program), and every uid is cross-checked
// against the program's actual roster before a notification is queued -
// a coach passing an arbitrary uid list can't reach members outside their
// own batch. Reuses the same notifications-doc shape and sendPendingNotifications
// pipeline as announcements, so it inherits the same delivery/retry behavior
// for free instead of sending FCM directly from here.
export const sendBulkNotification = onCall({ region }, async request => {
  const auth = requireUser(request);
  const role = auth.token.role;
  const programId = String(request.data?.programId ?? '');
  const title = String(request.data?.title ?? '').trim().slice(0, 120);
  const body = String(request.data?.body ?? '').trim().slice(0, 200);
  const requestedUids: string[] = Array.isArray(request.data?.uids) ? request.data.uids.map(String).filter(Boolean) : [];
  if (!programId || !title || !body || !requestedUids.length) {
    throw new HttpsError('invalid-argument', 'programId, title, body, and at least one uid are required');
  }
  if (requestedUids.length > 200) throw new HttpsError('invalid-argument', 'Too many recipients in one call (max 200)');

  if (role !== 'admin' && role !== 'super_admin') {
    if (role !== 'coach') throw new HttpsError('permission-denied', 'Staff only');
    const program = await db.doc(`programs/${programId}`).get();
    if (program.get('coachId') !== auth.uid) throw new HttpsError('permission-denied', 'Not your program');
  }

  const rosterSnap = await db.collection('programMembers').where('programId', '==', programId).get();
  const rosterUids = new Set(rosterSnap.docs.map(doc => String(doc.get('uid') ?? '')));
  const targets = requestedUids.filter(uid => rosterUids.has(uid));
  if (!targets.length) throw new HttpsError('invalid-argument', 'None of the given uids are members of this program');

  const batch = db.batch();
  targets.forEach(uid => {
    batch.set(db.collection('notifications').doc(), {
      userId: uid, profileId: null, title, body, type: 'coach_message',
      status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp(),
    });
  });
  await batch.commit();

  await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: role, action: 'bulk_notify', entityType: 'program', entityId: programId, metadata: { recipientCount: targets.length, title }, createdAt: FieldValue.serverTimestamp() });

  return { sent: targets.length };
});

// Was a full erase-everything sweep. Changed to anonymize instead of delete
// for the health-metric collections - a BP reading or a walk distance has
// real value in aggregate for understanding what actually helps people
// manage and reverse conditions like type-2 diabetes, and that value
// doesn't depend on knowing whose reading it was. Only identifying context
// (profile, name/contact, consultations, coach notes, device identifiers,
// generated narratives that reference the person, uploaded files that show
// a name on their face) is deleted outright; the raw metric collections are
// stripped of userId/profileId (and any free-text field that could carry a
// name) and left in place. Firestore rules gate every read in those
// collections on `resource.data.userId == request.auth.uid`, so once that
// field is gone the record becomes unreadable by any individual user's
// client - it only exists for internal, aggregate analysis from here on.
export const processApprovedDeletions = onSchedule({ schedule: 'every 60 minutes', timeZone: 'Asia/Kolkata', region }, async () => {
  const requests = await db.collection('deletionRequests').where('status', '==', 'approved').limit(10).get();

  const personalCollections = ['profiles', 'dailyActions', 'weeklyReports', 'sugarStories', 'consultations', 'userPrograms', 'programPlans', 'expertNotes', 'notifications', 'deviceConnections'];
  const anonymizeCollections = ['glucoseReadings', 'bpReadings', 'sleepLogs', 'walkLogs', 'weightLogs', 'medicationLogs', 'checklistLogs', 'dailyCheckins', 'labReports'];

  for (const request of requests.docs) {
    const uid = request.get('userId'); if (!uid) continue;
    await request.ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });

    for (const name of personalCollections) {
      const docs = await db.collection(name).where('userId', '==', uid).get();
      for (let offset = 0; offset < docs.docs.length; offset += 400) { const batch = db.batch(); docs.docs.slice(offset, offset + 400).forEach(doc => batch.delete(doc.ref)); await batch.commit(); }
    }

    for (const name of anonymizeCollections) {
      const docs = await db.collection(name).where('userId', '==', uid).get();
      for (let offset = 0; offset < docs.docs.length; offset += 400) {
        const batch = db.batch();
        docs.docs.slice(offset, offset + 400).forEach(doc => {
          const update: Record<string, unknown> = { userId: FieldValue.delete(), profileId: FieldValue.delete(), anonymizedAt: FieldValue.serverTimestamp() };
          // labReports carries free-text fields (lab name, notes) and a
          // Storage fileUrl - the underlying file is deleted outright below
          // (a scanned report shows a name on its face), so the dangling
          // URL and any free text that could identify someone go with it.
          if (name === 'labReports') { update.labName = FieldValue.delete(); update.notes = FieldValue.delete(); update.fileUrl = FieldValue.delete(); }
          batch.set(doc.ref, update, { merge: true });
        });
        await batch.commit();
      }
    }

    // programMembers and the batchStats/checkedInMembers marker are tied to
    // program-membership identity (visible to a coach as roster rows), not
    // a standalone health metric - deleted the same as the personal
    // collections above, not anonymized.
    const roster = await db.collection('programMembers').where('uid', '==', uid).get();
    const programIds = new Set(roster.docs.map(doc => String(doc.get('programId') ?? '')).filter(Boolean));
    for (let offset = 0; offset < roster.docs.length; offset += 400) { const batch = db.batch(); roster.docs.slice(offset, offset + 400).forEach(doc => batch.delete(doc.ref)); await batch.commit(); }
    // Sweep this user's daily check-in marker out of every batchStats day for
    // every program they were ever in (marker doc id is the uid itself -
    // deleting a non-existent doc is a safe no-op).
    for (const programId of programIds) {
      const days = await db.collection('batchStats').where('programId', '==', programId).get();
      for (let offset = 0; offset < days.docs.length; offset += 400) {
        const batch = db.batch();
        days.docs.slice(offset, offset + 400).forEach(day => batch.delete(day.ref.collection('checkedInMembers').doc(uid)));
        await batch.commit();
      }
    }
    // Raw uploaded files (lab scans, profile photos, consultation
    // attachments, generated PDF reports) almost always show identifying
    // detail on their face - deleted outright, never anonymized in place.
    for (const prefix of [`users/${uid}/`, `lab-reports/${uid}/`, `consultation-attachments/${uid}/`, `reports/${uid}/`]) await getStorage().bucket().deleteFiles({ prefix });
    await db.doc(`users/${uid}`).delete();
    await getAuth().deleteUser(uid);
    await request.ref.set({ status: 'completed', completedAt: FieldValue.serverTimestamp(), userIdHash: createHash('sha256').update(uid).digest('hex'), userId: FieldValue.delete() }, { merge: true });
  }
});
