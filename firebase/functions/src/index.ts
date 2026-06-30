import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore, Timestamp } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { getStorage } from 'firebase-admin/storage';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { onCall, onRequest, HttpsError } from 'firebase-functions/v2/https';
import { defineSecret } from 'firebase-functions/params';
import * as functions from 'firebase-functions/v1';
import { createHash, createHmac, timingSafeEqual } from 'node:crypto';

initializeApp();
const db = getFirestore();
const region = 'asia-south1';
const razorpayWebhookSecret = defineSecret('RAZORPAY_WEBHOOK_SECRET');
const razorpayKeyId = defineSecret('RAZORPAY_KEY_ID');
const razorpayKeySecret = defineSecret('RAZORPAY_KEY_SECRET');

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

export const generateDailyActions = onSchedule({ schedule: '0 5 * * *', timeZone: 'Asia/Kolkata', region }, async () => {
  const users = await db.collection('users').where('status', '==', 'active').get();
  const day = new Date().toISOString().slice(0, 10); const batch = db.batch();
  users.docs.forEach(user => batch.set(db.doc(`dailyActions/${user.id}_${day}`), { userId: user.id, profileId: user.id, dateKey: day, title: 'Walk 15 minutes after dinner', reason: 'A short post-meal walk can support your health rhythm.', status: 'pending', createdAt: FieldValue.serverTimestamp() }, { merge: true }));
  await batch.commit();
});

export const generateWeeklyReports = onSchedule({ schedule: '0 6 * * 1', timeZone: 'Asia/Kolkata', region }, async () => {
  const users = await db.collection('users').where('status', '==', 'active').get();
  const end = Timestamp.now(); const start = Timestamp.fromMillis(end.toMillis() - 7 * 86400000);
  for (const user of users.docs) {
    const glucose = await db.collection('glucoseReadings').where('userId', '==', user.id).where('measuredAt', '>=', start).get();
    const values = glucose.docs.map(x => Number(x.data().value)).filter(Number.isFinite);
    const average = values.length ? Math.round(values.reduce((a,b) => a+b, 0) / values.length) : null;
    await db.collection('weeklyReports').add({ userId: user.id, profileId: user.id, periodStart: start, periodEnd: end, glucoseAverage: average, glucoseLogCount: values.length, consistency: values.length >= 4 ? 'good' : 'building', recommendation: 'Focus on one consistent daily action this week.', createdAt: FieldValue.serverTimestamp() });
  }
});

export const sendPendingNotifications = onSchedule({ schedule: 'every 5 minutes', timeZone: 'Asia/Kolkata', region }, async () => {
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

function validRazorpaySignature(rawBody: Buffer, signature: string | undefined, secret: string) {
  if (!signature) return false; const expected = createHmac('sha256', secret).update(rawBody).digest('hex');
  const supplied = Buffer.from(signature); const calculated = Buffer.from(expected);
  return supplied.length === calculated.length && timingSafeEqual(supplied, calculated);
}

export const razorpayWebhook = onRequest({ region, secrets: [razorpayWebhookSecret] }, async (req, res) => {
  if (req.method !== 'POST') { res.status(405).send('Method not allowed'); return; }
  if (!validRazorpaySignature(req.rawBody, req.header('x-razorpay-signature'), razorpayWebhookSecret.value())) { res.status(401).send('Invalid signature'); return; }
  const eventId = req.header('x-razorpay-event-id') ?? createHmac('sha256', razorpayWebhookSecret.value()).update(req.rawBody).digest('hex');
  const eventRef = db.doc(`paymentWebhookEvents/${eventId}`);
  if ((await eventRef.get()).exists) { res.status(200).send('Already processed'); return; }
  const payment = req.body?.payload?.payment?.entity ?? req.body?.payload?.payment_link?.entity ?? {};
  const notes = payment.notes ?? {}; const status = String(payment.status ?? 'captured');
  await db.runTransaction(async transaction => {
    const existingEvent = await transaction.get(eventRef); if (existingEvent.exists) return;
    const orderRef = notes.orderId ? db.doc(`orders/${notes.orderId}`) : null;
    const orderSnap = orderRef ? await transaction.get(orderRef) : null;
    const consultationRef = notes.consultationId ? db.doc(`consultations/${notes.consultationId}`) : null;
    const consultationSnap = consultationRef ? await transaction.get(consultationRef) : null;
    const slotRef = consultationSnap?.get('slotId') ? db.doc(`consultationSlots/${consultationSnap.get('slotId')}`) : null;
    if (slotRef) await transaction.get(slotRef);
    const items = Array.isArray(orderSnap?.get('items')) ? orderSnap!.get('items') as Array<{productId?: string; quantity?: number}> : [];
    const pricedItems = items.filter(item => item.productId);
    const productSnaps = await Promise.all(pricedItems.map(item => transaction.get(db.doc(`products/${item.productId}`))));
    transaction.create(eventRef, { provider: 'razorpay', type: req.body?.event ?? 'unknown', paymentId: payment.id ?? null, createdAt: FieldValue.serverTimestamp() });
    if (consultationRef) {
      transaction.set(consultationRef, { paymentStatus: status === 'captured' ? 'paid' : status, paymentId: payment.id, status: status === 'captured' ? 'confirmed' : 'payment_pending', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      if (slotRef && status === 'captured') transaction.set(slotRef, { booked: true, active: false, bookedBy: consultationSnap?.get('userId'), consultationId: consultationRef.id, heldBy: FieldValue.delete(), heldUntil: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
    if (orderRef) {
      const wasPaid = orderSnap?.get('paymentStatus') === 'paid';
      transaction.set(orderRef, { paymentStatus: status === 'captured' ? 'paid' : status, paymentId: payment.id, orderStatus: status === 'captured' ? 'confirmed' : 'pending', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      if (status === 'captured' && !wasPaid) productSnaps.forEach((product, i) => {
        const quantity = Math.max(1, Number(pricedItems[i]?.quantity ?? 1)); const stock = Number(product.get('stock') ?? 0);
        if (stock < quantity) throw new HttpsError('failed-precondition', `Insufficient stock for ${product.id}`);
        transaction.update(product.ref, { stock: stock - quantity, updatedAt: FieldValue.serverTimestamp() });
      });
    }
  });
  res.status(200).send('OK');
});

function requireUser(request: { auth?: { uid: string; token: Record<string, unknown> } }) { if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in required'); return request.auth; }

export const createPaymentOrder = onCall({ region, secrets: [razorpayKeyId, razorpayKeySecret] }, async request => {
  const auth = requireUser(request); const kind = String(request.data?.kind ?? ''); const entityId = String(request.data?.entityId ?? '');
  if (!['order', 'consultation'].includes(kind) || !entityId) throw new HttpsError('invalid-argument', 'Valid payment entity required');
  const collection = kind === 'order' ? 'orders' : 'consultations'; const ref = db.doc(`${collection}/${entityId}`); const snap = await ref.get();
  if (!snap.exists || snap.get('userId') !== auth.uid) throw new HttpsError('permission-denied', 'Payment entity is not available');
  if (snap.get('paymentStatus') === 'paid') throw new HttpsError('failed-precondition', 'Payment is already complete');

  if (kind === 'consultation') {
    const slotId = String(snap.get('slotId') ?? ''); if (!slotId) throw new HttpsError('failed-precondition', 'Select an available consultation slot');
    await db.runTransaction(async transaction => {
      const slotRef = db.doc(`consultationSlots/${slotId}`); const slot = await transaction.get(slotRef); const heldUntil = slot.get('heldUntil') as Timestamp | null;
      const heldByAnother = slot.get('heldBy') && slot.get('heldBy') !== auth.uid && heldUntil && heldUntil.toMillis() > Date.now();
      if (!slot.exists || slot.get('active') !== true || slot.get('booked') === true || heldByAnother) throw new HttpsError('already-exists', 'That slot is no longer available');
      transaction.update(slotRef, { heldBy: auth.uid, heldUntil: Timestamp.fromMillis(Date.now() + 15 * 60000), updatedAt: FieldValue.serverTimestamp() });
    });
  }

  let amountRupees = 0;
  if (kind === 'order') {
    const items = Array.isArray(snap.get('items')) ? snap.get('items') as Array<{productId?: string; quantity?: number}> : [];
    if (!items.length) throw new HttpsError('failed-precondition', 'Cart is empty');
    for (const item of items) {
      if (!item.productId) throw new HttpsError('invalid-argument', 'Invalid product');
      const product = await db.doc(`products/${item.productId}`).get(); const quantity = Math.max(1, Math.min(20, Number(item.quantity ?? 1)));
      if (!product.exists || product.get('active') !== true || Number(product.get('stock') ?? 0) < quantity) throw new HttpsError('failed-precondition', 'A product is unavailable');
      amountRupees += Number(product.get('price')) * quantity;
    }
  } else {
    const type = String(snap.get('consultationType') ?? 'diabetes_lifestyle');
    const approvedPrices: Record<string, number> = { diabetes_lifestyle: 999, diet_review: 799, yoga: 699, naturopathy: 699, follow_up: 499 };
    amountRupees = approvedPrices[type] ?? approvedPrices.diabetes_lifestyle;
  }
  if (!Number.isFinite(amountRupees) || amountRupees < 1) throw new HttpsError('failed-precondition', 'Invalid amount');
  const amount = Math.round(amountRupees * 100); const authorization = Buffer.from(`${razorpayKeyId.value()}:${razorpayKeySecret.value()}`).toString('base64');
  const response = await fetch('https://api.razorpay.com/v1/orders', { method: 'POST', headers: { authorization: `Basic ${authorization}`, 'content-type': 'application/json' }, body: JSON.stringify({ amount, currency: 'INR', receipt: `${kind}_${entityId}`.slice(0, 40), notes: { userId: auth.uid, [`${kind}Id`]: entityId } }) });
  if (!response.ok) { console.error('Razorpay order failed', response.status, await response.text()); throw new HttpsError('internal', 'Payment could not be initialized'); }
  const paymentOrder = await response.json() as { id: string; amount: number; currency: string };
  await ref.set({ razorpayOrderId: paymentOrder.id, total: amountRupees, paymentStatus: 'pending', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  return { orderId: paymentOrder.id, amount: paymentOrder.amount, currency: paymentOrder.currency, keyId: razorpayKeyId.value(), entityId, kind };
});

export const redeemProgramCode = onCall({ region }, async request => {
  const auth = requireUser(request); const code = String(request.data?.code ?? '').trim().toUpperCase();
  if (!/^[A-Z0-9-]{4,32}$/.test(code)) throw new HttpsError('invalid-argument', 'Enter a valid program code');
  const hash = createHash('sha256').update(code).digest('hex'); const codeRef = db.doc(`programCodes/${hash}`);
  return db.runTransaction(async transaction => {
    const codeSnap = await transaction.get(codeRef); if (!codeSnap.exists || codeSnap.get('active') !== true) throw new HttpsError('not-found', 'Program code not recognized');
    const expiresAt = codeSnap.get('expiresAt') as Timestamp | null; if (expiresAt && expiresAt.toMillis() < Date.now()) throw new HttpsError('failed-precondition', 'Program code has expired');
    const uses = Number(codeSnap.get('uses') ?? 0); const maxUses = Number(codeSnap.get('maxUses') ?? 1); if (uses >= maxUses) throw new HttpsError('resource-exhausted', 'Program code has reached its usage limit');
    const programId = String(codeSnap.get('programId')); const durationWeeks = Math.max(1, Number(codeSnap.get('durationWeeks') ?? 24));
    const userProgramRef = db.doc(`userPrograms/${auth.uid}_${programId}`); const existing = await transaction.get(userProgramRef);
    if (!existing.exists) {
      transaction.create(userProgramRef, { userProgramId: userProgramRef.id, userId: auth.uid, profileId: auth.uid, programId, status: 'active', startDate: FieldValue.serverTimestamp(), endDate: Timestamp.fromMillis(Date.now() + durationWeeks * 7 * 86400000), currentWeek: 1, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
      transaction.update(codeRef, { uses: uses + 1, updatedAt: FieldValue.serverTimestamp() });
    }
    transaction.set(db.doc(`users/${auth.uid}`), { programActive: true, activeProgramId: programId, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { activated: true, programId, userProgramId: userProgramRef.id };
  });
});

export const requestDataExport = onCall({ region }, async request => {
  const auth = requireUser(request); await db.collection('dataExportRequests').add({ userId: auth.uid, status: 'requested', createdAt: FieldValue.serverTimestamp() }); return { accepted: true };
});
export const requestAccountDeletion = onCall({ region }, async request => {
  const auth = requireUser(request); await db.collection('deletionRequests').add({ userId: auth.uid, status: 'requested', createdAt: FieldValue.serverTimestamp() }); return { accepted: true };
});

export const exportUserData = onDocumentCreated({ document: 'dataExportRequests/{requestId}', region }, async event => {
  const request = event.data; if (!request) return; const uid = request.get('userId'); if (!uid) return;
  await request.ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  const names = ['users','profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','waterLogs','mealLogs','medications','medicationLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections','orders'];
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
  const ownedCollections = ['profiles','glucoseReadings','bpReadings','sleepLogs','walkLogs','weightLogs','waterLogs','mealLogs','medications','medicationLogs','labReports','dailyCheckins','dailyActions','weeklyReports','sugarStories','consultations','userPrograms','programPlans','checklistLogs','expertNotes','notifications','deviceConnections','carts','orders'];
  for (const request of requests.docs) {
    const uid = request.get('userId'); if (!uid) continue;
    await request.ref.set({ status: 'processing', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    for (const name of ownedCollections) {
      const docs = await db.collection(name).where('userId', '==', uid).get();
      for (let offset = 0; offset < docs.docs.length; offset += 400) { const batch = db.batch(); docs.docs.slice(offset, offset + 400).forEach(doc => batch.delete(doc.ref)); await batch.commit(); }
    }
    for (const prefix of [`users/${uid}/`, `meal-photos/${uid}/`, `lab-reports/${uid}/`, `consultation-attachments/${uid}/`, `reports/${uid}/`]) await getStorage().bucket().deleteFiles({ prefix });
    await db.doc(`users/${uid}`).delete();
    await getAuth().deleteUser(uid);
    await request.ref.set({ status: 'completed', completedAt: FieldValue.serverTimestamp(), userIdHash: createHash('sha256').update(uid).digest('hex'), userId: FieldValue.delete() }, { merge: true });
  }
});
