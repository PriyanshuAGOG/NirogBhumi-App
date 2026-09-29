// Seeds the Firebase emulators (Auth + Firestore) with a small, realistic world:
// one admin, two coaches each running a batch, members in each batch, and a few
// support/report/deletion items. Runs inside `firebase emulators:exec`, so the
// emulator host env vars are already set - it can never touch production.
import { createRequire } from 'node:module';
const require = createRequire(new URL('../firebase/functions/package.json', import.meta.url));
process.env.GCLOUD_PROJECT = 'demo-nirog-bhumi';
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, Timestamp, FieldValue } = require('firebase-admin/firestore');

initializeApp();
const auth = getAuth();
const db = getFirestore();
export const PASSWORD = 'Passw0rd!';
const daysAgo = (n) => Timestamp.fromMillis(Date.now() - n * 86_400_000);

async function user(uid, email, claims, profile) {
  await auth.createUser({ uid, email, password: PASSWORD, emailVerified: true });
  if (claims) await auth.setCustomUserClaims(uid, claims);
  await db.doc(`users/${uid}`).set({ userId: uid, email, role: claims?.role ?? 'user', status: 'active', createdAt: daysAgo(30), ...profile });
}

await user('admin1', 'admin@test.dev', { role: 'admin' }, { fullName: 'Admin One' });
await user('coachA', 'coacha@test.dev', { role: 'coach', perms: ['moderation', 'batches', 'announcements', 'calendar', 'programs', 'support', 'members'] }, { fullName: 'Coach Anita' });
await user('coachB', 'coachb@test.dev', { role: 'coach' }, { fullName: 'Coach Bharat' });

await db.doc('programs/progA').set({ name: 'July Batch', coachId: 'coachA', coachName: 'Coach Anita', durationWeeks: 6, memberCount: 1, createdAt: daysAgo(20) });
await db.doc('programs/progB').set({ name: 'August Batch', coachId: 'coachB', coachName: 'Coach Bharat', durationWeeks: 6, memberCount: 1, createdAt: daysAgo(10) });

const enrolled = (programId, programName) => ({ programActive: true, activeProgramId: programId, activeProgramName: programName, programDurationDays: 42 });
await user('memA', 'asha@test.dev', null, { fullName: 'Asha Member', lastCheckinAt: daysAgo(6), ...enrolled('progA', 'July Batch') });
await user('memB', 'ravi@test.dev', null, { fullName: 'Ravi Member', lastCheckinAt: daysAgo(1), ...enrolled('progB', 'August Batch') });
await user('newbie', 'newbie@test.dev', null, { fullName: 'New Person' });
await user('walkin', 'walkin@test.dev', null, { fullName: 'Walk In' });
await user('memDel', 'leaving@test.dev', null, { fullName: 'Leaving Member', consent: { research: false } });

await db.doc('programMembers/progA_memA').set({ programId: 'progA', uid: 'memA', name: 'Asha Member', status: 'active', joinedAt: daysAgo(15), lastCheckinAt: daysAgo(6) });
await db.doc('programMembers/progB_memB').set({ programId: 'progB', uid: 'memB', name: 'Ravi Member', status: 'active', joinedAt: daysAgo(9), lastCheckinAt: daysAgo(1) });

await db.collection('glucoseReadings').add({ userId: 'memA', value: 118, unit: 'mg/dL', context: 'Fasting', createdAt: daysAgo(1), measuredAt: daysAgo(1) });
await db.collection('glucoseReadings').add({ userId: 'memB', value: 131, unit: 'mg/dL', context: 'Fasting', createdAt: daysAgo(1), measuredAt: daysAgo(1) });

await db.collection('supportRequests').add({ userId: 'memA', status: 'open', subject: 'Cannot see my plan', message: 'Help please', createdAt: daysAgo(1) });
await db.collection('reportedMessages').add({ programId: 'progA', messageId: 'm1', reportedText: 'spammy text in A', reportedUserId: 'memA', reporterId: 'memB', status: 'open', createdAt: daysAgo(1) });
await db.collection('reportedMessages').add({ programId: 'progB', messageId: 'm2', reportedText: 'spammy text in B', reportedUserId: 'memB', reporterId: 'memA', status: 'open', createdAt: daysAgo(1) });

await db.collection('consultations').doc('consA').set({ userId: 'memA', consultationType: 'Diet review', concern: 'My fasting sugar is high on weekends.', preferredWindow: 'Evening', shareRecentLogs: true, status: 'pending', paymentStatus: 'pending', createdAt: daysAgo(2) });
await db.collection('consultations').doc('consB').set({ userId: 'memB', consultationType: 'Naturopathy', concern: 'Looking for a gentle daily routine.', preferredWindow: 'Morning', status: 'pending', paymentStatus: 'pending', createdAt: daysAgo(1) });
await db.collection('deletionRequests').add({ userId: 'memDel', status: 'scheduled', source: 'app', scheduledFor: Timestamp.fromMillis(Date.now() + 5 * 86_400_000), attempts: 0, createdAt: daysAgo(2) });
await db.collection('programInvites').doc('email_invitee@test.dev').set({ contact: 'invitee@test.dev', contactType: 'email', programId: 'progA', programName: 'July Batch', createdAt: FieldValue.serverTimestamp(), consumedAt: null, consumedByUid: null });

console.log('seeded');
