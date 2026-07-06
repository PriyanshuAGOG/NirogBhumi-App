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
});

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
  const beforeAt = before?.lastCheckinAt?.toMillis?.() ?? null;
  const afterAt = after.lastCheckinAt?.toMillis?.() ?? null;
  if (beforeAt === afterAt) return;
  const roster = await db.collection('programMembers').where('uid', '==', event.params.uid).get();
  if (roster.empty) return;
  const batch = db.batch();
  roster.docs.forEach(d => batch.set(d.ref, { lastCheckinAt: after.lastCheckinAt, checkinStreak: after.checkinStreak ?? null }, { merge: true }));
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

// Fans a new coach/admin announcement out to every member of that program as
// a push notification. type:'announcement' (like 'critical_alert') bypasses
// sendPendingNotifications()'s quiet-hours defer and daily cap, since an
// announcement is a deliberate one-off staff broadcast, not a routine
// reminder - a member should never miss "class moved to 6pm" because it
// landed during quiet hours or after their 3rd reminder that day.
export const onAnnouncementCreate = onDocumentCreated({ document: 'announcements/{id}', region }, async event => {
  const snap = event.data; if (!snap) return;
  const data = snap.data();
  const programId = String(data.programId ?? ''); if (!programId) return;
  const authorId = String(data.authorId ?? '');
  const title = String(data.title ?? 'New announcement').slice(0, 120);
  const body = String(data.body ?? '').slice(0, 200);
  const members = await db.collection('programMembers').where('programId', '==', programId).get();
  for (let offset = 0; offset < members.docs.length; offset += 400) {
    const batch = db.batch();
    members.docs.slice(offset, offset + 400).forEach(member => {
      const uid = String(member.get('uid') ?? '');
      if (!uid || uid === authorId) return; // the author doesn't need a push about their own post
      batch.set(db.collection('notifications').doc(), {
        userId: uid, profileId: null, title, body, type: 'announcement',
        status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp(),
      });
    });
    await batch.commit();
  }
});

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
    for (let i = 0; i < uids.length; i += 30) {
      const chunk = uids.slice(i, i + 30);
      const walks = await db.collection('walkLogs').where('userId', 'in', chunk).where('createdAt', '>=', monthStartTs).get();
      walks.docs.forEach(w => { totalMinutes += Number(w.get('minutes') ?? 0); });
    }
    await db.doc(`batchStats/${programId}_${today}`).set({ programId, dayKey: today, collectiveMinutes: Math.round(totalMinutes), memberCount: uids.length, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
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
});

function requireUser(request: { auth?: { uid: string; token: Record<string, unknown> } }) { if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in required'); return request.auth; }

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
  const durationDays = Math.round(Number(programDoc.get('durationDays') ?? 0)) || 0;

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
  const auth = requireUser(request); await db.collection('deletionRequests').add({ userId: auth.uid, status: 'requested', createdAt: FieldValue.serverTimestamp() }); return { accepted: true };
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
  if (!uid || !['user', 'coach', 'admin'].includes(role)) throw new HttpsError('invalid-argument', 'A uid and a valid role (user|coach|admin) are required');
  if (uid === auth.uid && role !== 'admin') throw new HttpsError('failed-precondition', 'You cannot remove your own admin role');

  // A plain admin and super_admin were previously equal in enforcement here,
  // meaning any one admin account (compromised or malicious) could mint or
  // demote unlimited other admins. Only super_admin may now grant the admin
  // role, or change the role of an account that's already admin/super_admin.
  if (callerRole !== 'super_admin') {
    if (role === 'admin') throw new HttpsError('permission-denied', 'Only a super admin can grant the admin role');
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
  if (!['coach', 'admin'].includes(role)) throw new HttpsError('invalid-argument', 'Role must be coach or admin');
  if (role === 'admin' && callerRole !== 'super_admin') throw new HttpsError('permission-denied', 'Only a super admin can create an admin account');

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

export const processApprovedDeletions = onSchedule({ schedule: 'every 60 minutes', timeZone: 'Asia/Kolkata', region }, async () => {
  const requests = await db.collection('deletionRequests').where('status', '==', 'approved').limit(10).get();
  // programMembers and the batchStats/checkedInMembers marker were missing
  // from this list - both key documents by/contain the raw uid, so without
  // this a "completed" deletion still left the person's roster entry (and
  // their daily check-in marker) behind after userId was hashed off the
  // request doc, an incomplete-erasure bug for a health app.
  const ownedCollections = ['profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections','programMembers','medicationLogs'];
  for (const request of requests.docs) {
    const uid = request.get('userId'); if (!uid) continue;
    await request.ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    for (const name of ownedCollections) {
      const docs = await db.collection(name).where('userId', '==', uid).get();
      for (let offset = 0; offset < docs.docs.length; offset += 400) { const batch = db.batch(); docs.docs.slice(offset, offset + 400).forEach(doc => batch.delete(doc.ref)); await batch.commit(); }
    }
    // programMembers is keyed by "uid" on the roster doc itself (see
    // redeemProgramCode), not "userId" - the pass above won't match those.
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
    for (const prefix of [`users/${uid}/`, `lab-reports/${uid}/`, `consultation-attachments/${uid}/`, `reports/${uid}/`]) await getStorage().bucket().deleteFiles({ prefix });
    await db.doc(`users/${uid}`).delete();
    await getAuth().deleteUser(uid);
    await request.ref.set({ status: 'completed', completedAt: FieldValue.serverTimestamp(), userIdHash: createHash('sha256').update(uid).digest('hex'), userId: FieldValue.delete() }, { merge: true });
  }
});
