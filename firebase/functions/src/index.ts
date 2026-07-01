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
  if (value >= 300 || value <= 54) return 'critical';
  if ((type === 'fasting' && value > 125) || (type !== 'fasting' && value > 180)) return 'needs_attention';
  return 'in_range';
}

export const onGlucoseReadingCreate = onDocumentCreated({ document: 'glucoseReadings/{readingId}', region }, async event => {
  const snap = event.data; if (!snap) return;
  const data = snap.data(); const value = Number(data.value); const status = glucoseStatus(value, String(data.readingType));
  await snap.ref.set({ status, categorizedAt: FieldValue.serverTimestamp() }, { merge: true });
  await db.doc(`users/${data.userId}`).set({ latestMetrics: { fastingSugar: value, glucoseStatus: status, glucoseUpdatedAt: FieldValue.serverTimestamp() }, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  if (status === 'critical') await db.collection('notifications').add({ userId: data.userId, profileId: data.profileId, title: 'Please review this reading', body: 'Repeat the measurement and contact your doctor promptly, especially if you feel unwell.', type: 'reminder', status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
});

export const onBPReadingCreate = onDocumentCreated({ document: 'bpReadings/{readingId}', region }, async event => {
  const snap = event.data; if (!snap) return; const d = snap.data();
  const critical = Number(d.systolic) >= 180 || Number(d.diastolic) >= 120;
  await snap.ref.set({ status: critical ? 'critical' : 'recorded', categorizedAt: FieldValue.serverTimestamp() }, { merge: true });
  if (critical) await db.collection('notifications').add({ userId: d.userId, profileId: d.profileId, title: 'Please review your BP reading', body: 'Repeat the measurement and seek urgent medical advice, especially if you feel unwell.', type: 'reminder', status: 'scheduled', scheduledFor: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp() });
});

// Daily maintenance job. Runs once a day; on Mondays it also builds weekly
// reports. Keeping daily + weekly in one schedule keeps us to 3 Cloud
// Scheduler jobs total (this + notifications + deletions), inside the free tier.
export const generateDailyContent = onSchedule({ schedule: '0 5 * * *', timeZone: 'Asia/Kolkata', region }, async () => {
  const users = await db.collection('users').where('status', '==', 'active').get();
  const day = new Date().toISOString().slice(0, 10); const batch = db.batch();
  users.docs.forEach(user => batch.set(db.doc(`dailyActions/${user.id}_${day}`), { userId: user.id, profileId: user.id, dateKey: day, title: 'Walk 15 minutes after dinner', reason: 'A short post-meal walk can support your health rhythm.', status: 'pending', createdAt: FieldValue.serverTimestamp() }, { merge: true }));
  await batch.commit();

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
    } catch (error) { await doc.ref.set({ status: 'failed', failureReason: String(error), updatedAt: FieldValue.serverTimestamp() }, { merge: true }); }
  }
});

function requireUser(request: { auth?: { uid: string; token: Record<string, unknown> } }) { if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in required'); return request.auth; }

export const requestDataExport = onCall({ region }, async request => {
  const auth = requireUser(request); await db.collection('dataExportRequests').add({ userId: auth.uid, status: 'requested', createdAt: FieldValue.serverTimestamp() }); return { accepted: true };
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
  const auth = requireUser(request); const roles = Array.isArray(auth.token.roles) ? auth.token.roles : [];
  if (auth.token.role !== 'admin' && auth.token.role !== 'expert' && !roles.includes('admin')) throw new HttpsError('permission-denied', 'Staff role required');
  const data = request.data as Record<string, unknown>; await db.collection('auditLogs').add({ actorId: auth.uid, actorRole: auth.token.role ?? 'staff', action: data.action, entityType: data.entityType, entityId: data.entityId, metadata: data.metadata ?? {}, createdAt: FieldValue.serverTimestamp() }); return { logged: true };
});
export const queueDeletionRequest = onDocumentCreated({ document: 'deletionRequests/{requestId}', region }, async event => {
  await event.data?.ref.set({ status: 'awaiting_verification', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
});

export const processApprovedDeletions = onSchedule({ schedule: 'every 60 minutes', timeZone: 'Asia/Kolkata', region }, async () => {
  const requests = await db.collection('deletionRequests').where('status', '==', 'approved').limit(10).get();
  const ownedCollections = ['profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections'];
  for (const request of requests.docs) {
    const uid = request.get('userId'); if (!uid) continue;
    await request.ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    for (const name of ownedCollections) {
      const docs = await db.collection(name).where('userId', '==', uid).get();
      for (let offset = 0; offset < docs.docs.length; offset += 400) { const batch = db.batch(); docs.docs.slice(offset, offset + 400).forEach(doc => batch.delete(doc.ref)); await batch.commit(); }
    }
    for (const prefix of [`users/${uid}/`, `lab-reports/${uid}/`, `consultation-attachments/${uid}/`, `reports/${uid}/`]) await getStorage().bucket().deleteFiles({ prefix });
    await db.doc(`users/${uid}`).delete();
    await getAuth().deleteUser(uid);
    await request.ref.set({ status: 'completed', completedAt: FieldValue.serverTimestamp(), userIdHash: createHash('sha256').update(uid).digest('hex'), userId: FieldValue.delete() }, { merge: true });
  }
});
