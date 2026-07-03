import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore, Timestamp } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import * as functions from 'firebase-functions/v1';
import { createHash } from 'node:crypto';

initializeApp();
const db = getFirestore();
const region = 'asia-south1';

export const onUserCreate = functions.region(region).auth.user().onCreate(async user => {
  await db.doc(`users/${user.uid}`).set({ userId: user.uid, phone: user.phoneNumber ?? null, email: user.email ?? null, role: 'user', status: 'active', timezone: 'Asia/Kolkata', notificationPreferences: { quietHoursStart: '21:00', quietHoursEnd: '07:00', maxHealthReminders: 3 }, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
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
  for (const user of users.docs) {
    const glucose = await db.collection('glucoseReadings').where('userId', '==', user.id).where('measuredAt', '>=', start).get();
    const values = glucose.docs.map(x => Number(x.data().value)).filter(Number.isFinite);
    const average = values.length ? Math.round(values.reduce((a,b) => a+b, 0) / values.length) : null;
    await db.collection('weeklyReports').add({ userId: user.id, profileId: user.id, periodStart: start, periodEnd: end, glucoseAverage: average, glucoseLogCount: values.length, consistency: values.length >= 4 ? 'good' : 'building', recommendation: 'Focus on one consistent daily action this week.', createdAt: FieldValue.serverTimestamp() });
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
  const names = ['users','profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections'];
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

export const processApprovedDeletions = onSchedule({ schedule: 'every 60 minutes', timeZone: 'Asia/Kolkata', region }, async () => {
  const requests = await db.collection('deletionRequests').where('status', '==', 'approved').limit(10).get();
  // programMembers and the batchStats/checkedInMembers marker were missing
  // from this list - both key documents by/contain the raw uid, so without
  // this a "completed" deletion still left the person's roster entry (and
  // their daily check-in marker) behind after userId was hashed off the
  // request doc, an incomplete-erasure bug for a health app.
  const ownedCollections = ['profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections','programMembers'];
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
