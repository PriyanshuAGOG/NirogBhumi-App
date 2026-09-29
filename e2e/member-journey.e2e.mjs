// The member's whole journey, exactly as the Android app performs it, using the
// Firebase client SDK against the Auth + Firestore + Storage + Functions
// emulators (so the real security rules and the real Cloud Functions run):
// sign up -> profile -> consent -> join a batch -> log health data -> chat ->
// upload files -> export data -> schedule/cancel/complete account deletion.
// The final step runs the real deletion code with real Firestore/Storage/Auth
// dependencies (the unit tests use fakes). Never touches production.
import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth, signInWithEmailAndPassword } from 'firebase/auth';
import { addDoc, collection, connectFirestoreEmulator, deleteDoc, doc, getDoc, getDocs, getFirestore, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';
import { connectFunctionsEmulator, getFunctions, httpsCallable } from 'firebase/functions';
import { connectStorageEmulator, getBytes, getStorage, ref, uploadBytes } from 'firebase/storage';
import { require, step, expect, eventually, errorCode, summarize } from './lib.mjs';

const { initializeApp: initAdmin } = require('firebase-admin/app');
const { getFirestore: adminFirestore, Timestamp } = require('firebase-admin/firestore');
const { getStorage: adminStorage } = require('firebase-admin/storage');
const { getAuth: adminAuth } = require('firebase-admin/auth');
initAdmin({ storageBucket: 'demo-nirog-bhumi.appspot.com' });
const adb = adminFirestore();

const app = initializeApp({ apiKey: 'fake', projectId: 'demo-nirog-bhumi', storageBucket: 'demo-nirog-bhumi.appspot.com', appId: '1:1:web:journey' });
const auth = getAuth(app);
connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
const db = getFirestore(app);
connectFirestoreEmulator(db, '127.0.0.1', 8080);
const functions = getFunctions(app, 'asia-south1');
connectFunctionsEmulator(functions, '127.0.0.1', 5001);
const storage = getStorage(app);
connectStorageEmulator(storage, '127.0.0.1', 9199);
const call = (name, data) => httpsCallable(functions, name)(data).then((r) => r.data);

// A batch with a legacy code, as staff would set it up.
await adb.doc('programs/progJ').set({ name: 'Journey Batch', coachId: 'coachJ', code: 'JOURNEY1', durationWeeks: 6 });

console.log('\nMember journey');
let uid;
const email = 'journey@test.dev';

await step('sign up: the account gets its server-side user document', async () => {
  const cred = await createUserWithEmailAndPassword(auth, email, 'Passw0rd!');
  uid = cred.user.uid;
  const doc0 = await eventually(async () => { const s = await getDoc(doc(db, 'users', uid)); return s.exists() ? s.data() : null; }, 'onUserCreate should create users/{uid}');
  expect(doc0.role === 'user' && doc0.status === 'active', `unexpected server profile: ${JSON.stringify(doc0)}`);
});

await step('onboarding: profile, itemised consent and consent receipt save without permission errors', async () => {
  await setDoc(doc(db, 'users', uid), { fullName: 'Journey Member', age: 41, gender: 'Female', heightCm: 158, weightKg: 66, city: 'Pune', preferredLanguage: 'English', userId: uid, updatedAt: serverTimestamp() }, { merge: true });
  await setDoc(doc(db, 'users', uid), { consent: { healthData: true, expertReview: true, medicalDisclaimer: true, research: false, marketing: false, version: '2025-07' }, userId: uid, updatedAt: serverTimestamp() }, { merge: true });
  await addDoc(collection(db, 'users', uid, 'consentReceipts'), { purposes: { healthData: true }, version: '2025-07', acceptedAt: serverTimestamp(), platform: 'android', appVersion: '1.0.0' });
  const receipts = await getDocs(collection(db, 'users', uid, 'consentReceipts'));
  expect(receipts.size === 1, 'consent receipt should be stored');
});

await step('security: a member cannot self-enroll, self-promote or read another member', async () => {
  expect((await errorCode(() => updateDoc(doc(db, 'users', uid), { programActive: true, activeProgramId: 'progJ' }))) === 'permission-denied', 'self-enrollment must be denied');
  expect((await errorCode(() => updateDoc(doc(db, 'users', uid), { role: 'admin' }))) === 'permission-denied', 'role change must be denied');
  expect((await errorCode(() => getDoc(doc(db, 'users', 'someone-else')))) === 'permission-denied', 'reading another user must be denied');
});

await step('join a batch with its code; a bad code is refused with a clear error', async () => {
  expect((await errorCode(() => call('redeemProgramCode', { code: 'WRONG-CODE' }))) === 'functions/not-found', 'unknown code should be not-found');
  const res = await call('redeemProgramCode', { code: 'journey1' });
  expect(res.activeProgramId === 'progJ' && res.programActive === true, `redeem result wrong: ${JSON.stringify(res)}`);
  const me = (await getDoc(doc(db, 'users', uid))).data();
  expect(me.programActive === true && me.activeProgramId === 'progJ', 'profile should now show the enrollment');
  const heal = await call('ensureProgramMembership', {});
  expect(heal.repaired === false && heal.programId === 'progJ', `membership check wrong: ${JSON.stringify(heal)}`);
  expect((await getDoc(doc(db, 'programMembers', `progJ_${uid}`))).exists(), 'member can read their own roster entry');
});

await step('health logging: readings save, the server categorises them and mirrors the latest value', async () => {
  const g = await addDoc(collection(db, 'glucoseReadings'), { userId: uid, profileId: uid, value: 142, readingType: 'fasting', unit: 'mg/dL', measuredAt: new Date(), createdAt: serverTimestamp() });
  await addDoc(collection(db, 'bpReadings'), { userId: uid, profileId: uid, systolic: 122, diastolic: 80, createdAt: serverTimestamp() });
  await addDoc(collection(db, 'weightLogs'), { userId: uid, profileId: uid, valueKg: 66, createdAt: serverTimestamp() });
  const categorised = await eventually(async () => { const s = await getDoc(g); return s.get('status') ? s.data() : null; }, 'onGlucoseReadingCreate should categorise the reading');
  expect(categorised.status === 'needs_attention', `142 fasting should need attention, got ${categorised.status}`);
  const me = await eventually(async () => { const s = await getDoc(doc(db, 'users', uid)); return s.get('latestMetrics') ? s.data() : null; }, 'latestMetrics should be mirrored');
  expect(me.latestMetrics.fastingSugar === 142, 'latest fasting sugar mirrored');
  const list = await getDocs(query(collection(db, 'glucoseReadings'), where('userId', '==', uid)));
  expect(list.size === 1, 'member can list their own readings');
});

await step('batch chat: send a message and read it back; a member cannot pin', async () => {
  const m = await addDoc(collection(db, 'programChatMessages'), { programId: 'progJ', userId: uid, senderName: 'Journey Member', text: 'Hello batch!', photoUrl: null, audioUrl: null, audioDurationSec: null, replyTo: null, createdAt: serverTimestamp() });
  const list = await getDocs(query(collection(db, 'programChatMessages'), where('programId', '==', 'progJ')));
  expect(list.size === 1, 'message should be readable by the batch');
  expect((await errorCode(() => updateDoc(m, { pinned: true, pinnedBy: uid, pinnedAt: serverTimestamp() }))) === 'permission-denied', 'members cannot pin');
});

await step('ask your coach: the member can write and read their own thread', async () => {
  await addDoc(collection(db, 'coachInboxMessages'), { programId: 'progJ', memberUid: uid, fromUid: uid, senderName: 'Journey Member', senderRole: 'member', text: 'Is a 20 minute walk enough?', createdAt: serverTimestamp() });
  const thread = await getDocs(query(collection(db, 'coachInboxMessages'), where('programId', '==', 'progJ'), where('memberUid', '==', uid)));
  expect(thread.size === 1, 'thread readable');
});

let healthFilePath;
await step('uploads: private files and chat photos succeed; wrong owner or wrong type is refused', async () => {
  healthFilePath = `users/${uid}/health-file/abc123`;
  await uploadBytes(ref(storage, healthFilePath), new Uint8Array([37, 80, 68, 70]), { contentType: 'application/pdf' });
  await uploadBytes(ref(storage, `program-chat-photos/progJ/${uid}/p1`), new Uint8Array([1, 2, 3]), { contentType: 'image/jpeg' });
  await uploadBytes(ref(storage, `program-chat-audio/progJ/${uid}/a1`), new Uint8Array([1, 2, 3]), { contentType: 'audio/mp4' });
  expect((await getBytes(ref(storage, healthFilePath))).byteLength === 4, 'owner can read back their file');
  expect((await errorCode(() => uploadBytes(ref(storage, 'users/someone-else/health-file/x'), new Uint8Array([1]), { contentType: 'application/pdf' }))) === 'storage/unauthorized', 'cannot write into another member\'s folder');
  expect((await errorCode(() => uploadBytes(ref(storage, `users/${uid}/health-file/evil`), new Uint8Array([1]), { contentType: 'image/svg+xml' }))) === 'storage/unauthorized', 'svg must be refused');
  expect((await errorCode(() => uploadBytes(ref(storage, `program-chat-photos/otherBatch/${uid}/p`), new Uint8Array([1]), { contentType: 'image/jpeg' }))) === 'storage/unauthorized', 'cannot post photos into a batch you are not in');
});

await step('health file share link: refuses other members\' files; fails gracefully when signing is unavailable', async () => {
  expect((await errorCode(() => call('getHealthFileShareLink', { storagePath: 'users/someone-else/health-file/x' }))) === 'functions/permission-denied', 'must not sign someone else\'s file');
  const code = await errorCode(() => call('getHealthFileShareLink', { storagePath: healthFilePath }));
  expect(code === null || code === 'functions/failed-precondition', `unexpected error while signing: ${code}`);
});

let exportPath;
await step('data export: request -> file generated -> member downloads their own data (incl. chat + consent)', async () => {
  expect((await call('requestDataExport', {})).accepted === true, 'export accepted');
  expect((await errorCode(() => call('requestDataExport', {}))) === 'functions/resource-exhausted', 'second export within an hour is rate limited');
  const request = await eventually(async () => { const s = await getDocs(query(collection(db, 'dataExportRequests'), where('userId', '==', uid))); const d = s.docs[0]?.data(); return d?.status === 'completed' ? d : null; }, 'exportUserData should complete the request');
  exportPath = request.storagePath;
  const json = JSON.parse(new TextDecoder().decode(await getBytes(ref(storage, exportPath))));
  expect(json.users.length === 1 && json.users[0].fullName === 'Journey Member', 'export has the profile');
  expect(json.glucoseReadings.length === 1 && json.programChatMessages.length === 1 && json.consentReceipts.length === 1 && json.coachInboxMessages.length === 1, `export is missing sections: ${Object.entries(json).filter(([, v]) => Array.isArray(v)).map(([k, v]) => `${k}:${v.length}`).join(' ')}`);
});

await step('deletion: schedule (7 days), idempotent, visible to the member, cancel, re-schedule', async () => {
  const first = await call('requestAccountDeletion', {});
  expect(first.accepted && !first.alreadyPending && first.graceDays === 7, `schedule wrong: ${JSON.stringify(first)}`);
  expect(first.scheduledForMillis > Date.now() + 6 * 86_400_000, 'should be ~7 days out');
  expect((await call('requestAccountDeletion', {})).alreadyPending === true, 'second request is idempotent');
  const mine = await getDocs(query(collection(db, 'deletionRequests'), where('userId', '==', uid)));
  expect(mine.size === 1 && mine.docs[0].get('status') === 'scheduled', 'member can see their pending request');
  expect((await errorCode(() => addDoc(collection(db, 'deletionRequests'), { userId: uid, status: 'approved', createdAt: serverTimestamp() }))) === 'permission-denied', 'cannot forge a request');
  expect((await call('cancelAccountDeletion', {})).cancelled === true, 'cancel works');
  expect((await call('cancelAccountDeletion', {})).cancelled === false, 'nothing left to cancel');
  expect((await call('requestAccountDeletion', {})).alreadyPending === false, 'can schedule again after cancelling');
});

await step('deletion: once due, the real erasure removes data, files, subcollections and the login', async () => {
  const { processDueDeletions } = require('./lib/accountDeletion.js');
  const bucket = adminStorage().bucket();
  const req = (await adb.collection('deletionRequests').where('userId', '==', uid).where('status', '==', 'scheduled').get()).docs[0];
  await req.ref.set({ scheduledFor: Timestamp.fromMillis(Date.now() - 1000) }, { merge: true });
  const summary = await processDueDeletions({
    db: adb,
    deleteFiles: async (prefix) => { await bucket.deleteFiles({ prefix }); },
    deleteAuthUser: async (id) => { try { await adminAuth().deleteUser(id); } catch (e) { if (e.code !== 'auth/user-not-found') throw e; } },
  });
  // The console suite (run just before, same emulators) left two admin-approved requests
  // (memDel, newbie); the scheduler run erases those too, which proves the admin path end to end.
  expect(summary.completed >= 1 && summary.failed === 0 && summary.retrying === 0, `deletion should complete: ${JSON.stringify(summary)}`);
  for (const other of ['memDel', 'newbie']) {
    if ((await adb.collection('deletionRequests').where('userId', '==', other).get()).empty && (await adb.doc(`users/${other}`).get()).exists) continue; // console suite not run in this session
    expect(!(await adb.doc(`users/${other}`).get()).exists, `admin-approved deletion of ${other} should have erased the user document`);
    expect(await adminAuth().getUser(other).then(() => false, (e) => e.code === 'auth/user-not-found'), `admin-approved deletion of ${other} should have removed the login`);
  }
  expect(!(await adb.doc(`users/${uid}`).get()).exists, 'user doc gone');
  expect((await adb.collection(`users/${uid}/consentReceipts`).get()).empty, 'subcollections gone');
  for (const name of ['glucoseReadings', 'bpReadings', 'weightLogs', 'programChatMessages', 'dataExportRequests']) expect((await adb.collection(name).where('userId', '==', uid).get()).empty, `${name} erased`);
  expect((await adb.collection('coachInboxMessages').where('memberUid', '==', uid).get()).empty, 'inbox erased');
  expect(!(await adb.doc(`programMembers/progJ_${uid}`).get()).exists, 'roster entry gone');
  for (const p of [healthFilePath, `program-chat-photos/progJ/${uid}/p1`, `program-chat-audio/progJ/${uid}/a1`, exportPath]) expect(!(await bucket.file(p).exists())[0], `file ${p} should be deleted`);
  expect((await errorCode(() => signInWithEmailAndPassword(auth, email, 'Passw0rd!'))) === 'auth/user-not-found', 'login must be gone');
  const done = (await adb.collection('deletionRequests').get()).docs.find((d) => d.get('status') === 'completed');
  expect(done && !('userId' in done.data()) && done.get('mode') === 'erased', 'request closed without the uid');
});

process.exit(summarize('member-journey'));
