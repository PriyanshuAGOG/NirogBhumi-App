// Contract between the Android app and the security rules: the exact writes the
// app performs (payload shapes copied from HealthRepository.kt), as a signed-in
// member. A write that the rules silently reject shows up in the app as a
// generic failure - this is how "Export my data" and "Delete my account" shipped
// broken (they wrote deletionRequests/dataExportRequests directly, which the
// rules have always denied). Replaying every write here catches that class of bug.
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where, serverTimestamp } from 'firebase/firestore';

let testEnv;
before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-nirog-bhumi',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: 'localhost', port: 8080 },
  });
});
after(async () => { await testEnv.cleanup(); });

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'programs/progA'), { name: 'A', coachId: 'coach-a' });
    // A brand-new member has a bare doc; an enrolled one has the program fields.
    await setDoc(doc(db, 'users/newbie'), { userId: 'newbie' });
    await setDoc(doc(db, 'users/mem'), { userId: 'mem', role: 'user', status: 'active', programActive: true, activeProgramId: 'progA' });
    await setDoc(doc(db, 'programMembers/progA_mem'), { programId: 'progA', uid: 'mem', name: 'Mem' });
    await setDoc(doc(db, 'programChatMessages/m1'), { programId: 'progA', userId: 'someone', text: 'hi', reactions: {} });
  });
});

const member = (uid) => testEnv.authenticatedContext(uid, {}).firestore();
const ts = serverTimestamp;

describe('profile and consent', () => {
  it('saveProfile (merge) on a brand-new account and on an enrolled one', async () => {
    await assertSucceeds(setDoc(doc(member('newbie'), 'users/newbie'), { fullName: 'A', age: 30, gender: 'Female', heightCm: 160.5, weightKg: 60, city: 'Jaipur', preferredLanguage: 'English', userId: 'newbie', updatedAt: ts() }, { merge: true }));
    await assertSucceeds(setDoc(doc(member('mem'), 'users/mem'), { fullName: 'B', gender: '', userId: 'mem', updatedAt: ts() }, { merge: true }));
  });
  it('saveProfile for a doc that does not exist yet (first ever write)', async () => {
    await assertSucceeds(setDoc(doc(member('fresh'), 'users/fresh'), { fullName: 'F', userId: 'fresh', updatedAt: ts() }, { merge: true }));
  });
  it('optional consent toggle writes a nested consent map', async () => {
    await assertSucceeds(setDoc(doc(member('mem'), 'users/mem'), { consent: { research: true, version: '2025-07' }, userId: 'mem', updatedAt: ts() }, { merge: true }));
  });
  it('consent receipts are append-only', async () => {
    const db = member('mem');
    await assertSucceeds(addDoc(collection(db, 'users/mem/consentReceipts'), { purposes: { research: true }, version: '2025-07', acceptedAt: ts(), platform: 'android', appVersion: '1.0.0' }));
    await assertFails(addDoc(collection(member('newbie'), 'users/mem/consentReceipts'), { purposes: {}, version: 'x', acceptedAt: ts() }));
  });
  it('check-in completion updates streak fields on the member doc', async () => {
    await assertSucceeds(setDoc(doc(member('mem'), 'users/mem'), { checkinHourHint: 8, lastCheckinAt: ts(), checkinStreak: 3 }, { merge: true }));
  });
  it('cannot self-enroll, self-promote, or change status', async () => {
    await assertFails(setDoc(doc(member('newbie'), 'users/newbie'), { programActive: true, activeProgramId: 'progA' }, { merge: true }));
    await assertFails(setDoc(doc(member('newbie'), 'users/newbie'), { role: 'admin' }, { merge: true }));
    await assertFails(setDoc(doc(member('mem'), 'users/mem'), { status: 'suspended' }, { merge: true }));
  });
});

describe('health logs (addHealthLog / updateHealthLog / upsertUserRecord)', () => {
  const shapes = {
    glucoseReadings: { value: 110, unit: 'mg/dL', context: 'Fasting', measuredAt: new Date() },
    bpReadings: { systolic: 120, diastolic: 80 },
    sleepLogs: { hours: 7, quality: 'Good' },
    walkLogs: { minutes: 20 },
    weightLogs: { valueKg: 70 },
    medicationLogs: { name: 'Metformin', dose: '500mg', measuredAt: new Date() },
    checklistLogs: { item: 'Walk', done: true },
    labReports: { reportType: 'HbA1c', fileUrl: 'https://x' },
  };
  for (const [name, values] of Object.entries(shapes)) {
    it(`create + correct + delete own ${name}`, async () => {
      const db = member('mem');
      const ref = doc(collection(db, name));
      await assertSucceeds(setDoc(ref, { ...values, userId: 'mem', profileId: 'mem', createdAt: ts() }));
      await assertSucceeds(setDoc(ref, { note: 'edited' }, { merge: true }));
      await assertSucceeds(getDoc(ref));
      await assertSucceeds(deleteDoc(ref));
    });
    it(`cannot write ${name} as someone else`, async () => {
      await assertFails(setDoc(doc(collection(member('mem'), name)), { ...values, userId: 'other', createdAt: ts() }));
    });
  }
  it('device connections (Health Connect sync status) create + update', async () => {
    const db = member('mem');
    await assertSucceeds(setDoc(doc(db, 'deviceConnections/mem_hc'), { userId: 'mem', profileId: 'mem', provider: 'health_connect', status: 'connected', createdAt: ts(), updatedAt: ts() }, { merge: true }));
    await assertSucceeds(setDoc(doc(db, 'deviceConnections/mem_hc'), { userId: 'mem', lastSyncAt: ts() }, { merge: true }));
  });
  it('a consultation REQUEST (type, concern, preferred window) can be created but cannot self-confirm or carry a slot/link/expert', async () => {
    const db = member('mem');
    const request = { userId: 'mem', profileId: 'mem', consultationType: 'Diet review', concern: 'My fasting sugar is high on weekends', preferredWindow: 'Evening', shareRecentLogs: true, status: 'pending', paymentStatus: 'pending', createdAt: ts() };
    await assertSucceeds(setDoc(doc(collection(db, 'consultations')), request));
    await assertFails(setDoc(doc(collection(db, 'consultations')), { ...request, concern: 'x'.repeat(1001) }));
    await assertFails(setDoc(doc(collection(db, 'consultations')), { ...request, scheduledAt: new Date() }));
    await assertFails(setDoc(doc(collection(db, 'consultations')), { ...request, joinLink: 'https://evil.example' }));
    await assertFails(setDoc(doc(collection(db, 'consultations')), { ...request, status: 'confirmed' }));
  });
  it('a member cannot confirm or cancel their own consultation directly (staff confirm, the cancel callable cancels)', async () => {
    const db = member('mem');
    const ref = doc(collection(db, 'consultations'));
    await setDoc(ref, { userId: 'mem', status: 'pending', paymentStatus: 'pending', createdAt: ts() });
    await assertFails(setDoc(ref, { status: 'confirmed' }, { merge: true }));
    await assertFails(setDoc(ref, { status: 'cancelled' }, { merge: true }));
    await assertSucceeds(setDoc(ref, { concern: 'Updated details' }, { merge: true }));
  });
  it('family profile, support request, and a pending consultation can be created; a paid one cannot', async () => {
    const db = member('mem');
    await assertSucceeds(setDoc(doc(collection(db, 'profiles')), { userId: 'mem', profileId: 'x', name: 'Mum', createdAt: ts() }));
    await assertSucceeds(setDoc(doc(collection(db, 'supportRequests')), { userId: 'mem', status: 'open', message: 'help', createdAt: ts() }));
    await assertSucceeds(setDoc(doc(collection(db, 'consultations')), { userId: 'mem', status: 'payment_pending', paymentStatus: 'pending', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'consultations')), { userId: 'mem', status: 'confirmed', paymentStatus: 'paid', createdAt: ts() }));
  });
  it('a member cannot schedule their own push notifications, but can log a received one', async () => {
    const db = member('mem');
    await assertSucceeds(addDoc(collection(db, 'notifications'), { userId: 'mem', category: 'reminder', title: 't', body: 'b', route: 'dashboard', createdAt: ts() }));
    await assertFails(addDoc(collection(db, 'notifications'), { userId: 'mem', title: 't', body: 'b', status: 'scheduled', scheduledFor: ts(), createdAt: ts() }));
  });
});

describe('program chat and community', () => {
  it('sends a chat message (text, reply, photo, voice note)', async () => {
    const db = member('mem');
    await assertSucceeds(addDoc(collection(db, 'programChatMessages'), { programId: 'progA', userId: 'mem', senderName: 'Mem', text: 'hi', photoUrl: null, audioUrl: null, audioDurationSec: null, replyTo: { id: 'm1', sender: 'x', text: 'hi' }, createdAt: ts() }));
    await assertSucceeds(addDoc(collection(db, 'programChatMessages'), { programId: 'progA', userId: 'mem', senderName: 'Mem', text: '', photoUrl: 'https://p', audioUrl: 'https://a', audioDurationSec: 12, replyTo: null, createdAt: ts() }));
  });
  it('cannot post into a program you are not in, or as someone else', async () => {
    await assertFails(addDoc(collection(member('newbie'), 'programChatMessages'), { programId: 'progA', userId: 'newbie', senderName: 'N', text: 'hi', createdAt: ts() }));
    await assertFails(addDoc(collection(member('mem'), 'programChatMessages'), { programId: 'progA', userId: 'someone', senderName: 'N', text: 'hi', createdAt: ts() }));
  });
  it('reads the chat, reacts to a message, but cannot edit or pin someone else\'s message', async () => {
    const db = member('mem');
    await assertSucceeds(getDocs(query(collection(db, 'programChatMessages'), where('programId', '==', 'progA'))));
    await assertSucceeds(updateDoc(doc(db, 'programChatMessages/m1'), { reactions: { mem: '👍' } }));
    await assertFails(updateDoc(doc(db, 'programChatMessages/m1'), { text: 'edited' }));
    await assertFails(updateDoc(doc(db, 'programChatMessages/m1'), { pinned: true, pinnedBy: 'mem', pinnedAt: ts() }));
  });
  it('typing status, reporting a message, and the ask-your-coach inbox', async () => {
    const db = member('mem');
    await assertSucceeds(setDoc(doc(db, 'programTypingStatus/progA_mem'), { programId: 'progA', uid: 'mem', name: 'Mem', updatedAt: ts() }));
    await assertSucceeds(deleteDoc(doc(db, 'programTypingStatus/progA_mem')));
    await assertSucceeds(addDoc(collection(db, 'reportedMessages'), { messageId: 'm1', programId: 'progA', reportedText: 'x', reportedUserId: 'someone', reporterId: 'mem', status: 'open', createdAt: ts() }));
    await assertSucceeds(addDoc(collection(db, 'coachInboxMessages'), { programId: 'progA', memberUid: 'mem', fromUid: 'mem', senderName: 'Mem', senderRole: 'member', text: 'q', createdAt: ts() }));
    await assertSucceeds(getDocs(query(collection(db, 'coachInboxMessages'), where('programId', '==', 'progA'), where('memberUid', '==', 'mem'))));
  });
  it('roster self-service fields only: read markers and leaderboard opt-in', async () => {
    const db = member('mem');
    await assertSucceeds(getDoc(doc(db, 'programMembers/progA_mem')));
    await assertSucceeds(updateDoc(doc(db, 'programMembers/progA_mem'), { lastReadGeneralAt: ts() }));
    await assertSucceeds(updateDoc(doc(db, 'programMembers/progA_mem'), { leaderboardOptIn: true }));
    await assertFails(updateDoc(doc(db, 'programMembers/progA_mem'), { contributionKm: 999 }));
  });
});

describe('telemetry and privacy requests', () => {
  it('error reports can be filed for yourself but never read back', async () => {
    const db = member('mem');
    await assertSucceeds(addDoc(collection(db, 'errorReports'), { userId: 'mem', screen: 'x', message: 'm', code: null, resolved: false, createdAt: ts() }));
    await assertFails(getDocs(collection(db, 'errorReports')));
  });
  it('deletion / export requests are NOT client-writable (the app must use the callables)', async () => {
    const db = member('mem');
    await assertFails(addDoc(collection(db, 'deletionRequests'), { userId: 'mem', status: 'requested', createdAt: ts() }));
    await assertFails(addDoc(collection(db, 'dataExportRequests'), { userId: 'mem', status: 'requested', createdAt: ts() }));
  });
  it('the member can read their own deletion status (used by Data Controls)', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), 'deletionRequests/d1'), { userId: 'mem', status: 'scheduled' }); });
    await assertSucceeds(getDocs(query(collection(member('mem'), 'deletionRequests'), where('userId', '==', 'mem'))));
    await assertFails(getDocs(query(collection(member('newbie'), 'deletionRequests'), where('userId', '==', 'mem'))));
  });
  it('a member can read their own announcement feed but not the master documents', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'users/mem/announcements/a1'), { title: 't', createdAt: new Date() });
      await setDoc(doc(ctx.firestore(), 'announcements/a1'), { title: 't', authorId: 'coach-a', recipientUids: ['mem'] });
    });
    await assertSucceeds(getDocs(collection(member('mem'), 'users/mem/announcements')));
    await assertFails(getDoc(doc(member('mem'), 'announcements/a1')));
  });
});
