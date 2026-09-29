// Contract between the admin console and the security rules: every list query
// the console runs, as an admin and as a coach. Firestore rejects a list query
// unless the rules can prove EVERY document it could return is readable, so a
// page that "looks fine" against a handful of documents can still fail for real
// staff. These tests pin the exact query shapes the console uses (see
// console/src/lib/scope.ts) so a rules or console change can't silently break
// the coach console again.
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, orderBy, query, setDoc, where, Timestamp, serverTimestamp } from 'firebase/firestore';

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
    await setDoc(doc(db, 'programs/progB'), { name: 'B', coachId: 'coach-b' });
    await setDoc(doc(db, 'users/memA'), { userId: 'memA', programActive: true, activeProgramId: 'progA', role: 'user', status: 'active' });
    await setDoc(doc(db, 'users/memB'), { userId: 'memB', programActive: true, activeProgramId: 'progB', role: 'user', status: 'active' });
    await setDoc(doc(db, 'programMembers/progA_memA'), { programId: 'progA', uid: 'memA', name: 'Mem A', lastCheckinAt: Timestamp.now() });
    await setDoc(doc(db, 'programMembers/progB_memB'), { programId: 'progB', uid: 'memB', name: 'Mem B', lastCheckinAt: Timestamp.now() });
    await setDoc(doc(db, 'programCodes/CODEA'), { programId: 'progA', code: 'CODEA', active: true });
    await setDoc(doc(db, 'programCodes/CODEB'), { programId: 'progB', code: 'CODEB', active: true });
    await setDoc(doc(db, 'programInvites/email_a@x.com'), { programId: 'progA', contact: 'a@x.com', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'programInvites/email_b@x.com'), { programId: 'progB', contact: 'b@x.com', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'programEvents/eA'), { programId: 'progA', startsAt: Timestamp.now() });
    await setDoc(doc(db, 'programEvents/eB'), { programId: 'progB', startsAt: Timestamp.now() });
    await setDoc(doc(db, 'reportedMessages/rA'), { programId: 'progA', status: 'open', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'reportedMessages/rB'), { programId: 'progB', status: 'open', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'announcements/annA'), { authorId: 'coach-a', title: 'a', recipientUids: ['memA'], createdAt: Timestamp.now() });
    await setDoc(doc(db, 'announcements/annB'), { authorId: 'coach-b', title: 'b', recipientUids: ['memB'], createdAt: Timestamp.now() });
    await setDoc(doc(db, 'glucoseReadings/gA'), { userId: 'memA', value: 100, createdAt: Timestamp.now() });
    await setDoc(doc(db, 'glucoseReadings/gB'), { userId: 'memB', value: 100, createdAt: Timestamp.now() });
    await setDoc(doc(db, 'coachNotes/nA'), { targetUid: 'memA', authorId: 'coach-a', text: 'n', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'supportRequests/sA'), { userId: 'memA', status: 'open', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'consultations/cA'), { userId: 'memA', status: 'pending', paymentStatus: 'pending', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'deletionRequests/dA'), { userId: 'memA', status: 'scheduled', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'coachInboxMessages/iA'), { programId: 'progA', memberUid: 'memA', fromUid: 'memA', text: 'q', createdAt: Timestamp.now() });
  });
});

const coach = (uid) => testEnv.authenticatedContext(uid, { role: 'coach' }).firestore();
const admin = () => testEnv.authenticatedContext('admin-uid', { role: 'admin' }).firestore();
const list = (db, ...q) => getDocs(query(...q));

describe('coach console queries (coach-a coaches progA only)', () => {
  it('programs: only their own via coachId filter; unfiltered list is rejected', async () => {
    await assertSucceeds(list(coach('coach-a'), collection(coach('coach-a'), 'programs'), where('coachId', '==', 'coach-a')));
    await assertFails(getDocs(collection(coach('coach-a'), 'programs')));
  });

  it('rosters, events, codes, invites, reports: allowed for own program, refused for another', async () => {
    const db = coach('coach-a');
    for (const name of ['programMembers', 'programEvents', 'programCodes', 'programInvites', 'reportedMessages']) {
      await assertSucceeds(getDocs(query(collection(db, name), where('programId', 'in', ['progA']))));
      await assertSucceeds(getDocs(query(collection(db, name), where('programId', '==', 'progA'))));
      await assertFails(getDocs(query(collection(db, name), where('programId', '==', 'progB'))));
      await assertFails(getDocs(collection(db, name)));
    }
  });

  it('dashboard counts and roster for their programs work (count queries use the same filters)', async () => {
    const db = coach('coach-a');
    await assertSucceeds(getDocs(query(collection(db, 'programEvents'), where('programId', 'in', ['progA']), where('startsAt', '>=', Timestamp.fromMillis(0)))));
    await assertSucceeds(getDocs(query(collection(db, 'reportedMessages'), where('programId', 'in', ['progA']), where('status', '==', 'open'))));
    await assertSucceeds(getDocs(query(collection(db, 'supportRequests'), where('status', '==', 'open'))));
  });

  it('cannot list users or consultations at all', async () => {
    const db = coach('coach-a');
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(getDocs(query(collection(db, 'users'), where('activeProgramId', '==', 'progA'))));
    await assertFails(getDocs(collection(db, 'consultations')));
  });

  it('announcements: only their own posts (they list every recipient uid)', async () => {
    const db = coach('coach-a');
    await assertSucceeds(getDocs(query(collection(db, 'announcements'), where('authorId', '==', 'coach-a'))));
    await assertFails(getDocs(collection(db, 'announcements')));
    await assertFails(getDocs(query(collection(db, 'announcements'), where('authorId', '==', 'coach-b'))));
    await assertSucceeds(getDoc(doc(db, 'announcements/annA')));
    await assertFails(getDoc(doc(db, 'announcements/annB')));
  });

  it('member detail: their own member is fully readable, another coach\'s member is not', async () => {
    const db = coach('coach-a');
    await assertSucceeds(getDoc(doc(db, 'users/memA')));
    await assertSucceeds(getDocs(query(collection(db, 'glucoseReadings'), where('userId', '==', 'memA'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(query(collection(db, 'coachNotes'), where('targetUid', '==', 'memA'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(query(collection(db, 'programMembers'), where('uid', '==', 'memA'), where('programId', 'in', ['progA']))));
    await assertFails(getDoc(doc(db, 'users/memB')));
    await assertFails(getDocs(query(collection(db, 'glucoseReadings'), where('userId', '==', 'memB'), orderBy('createdAt', 'desc'))));
    await assertFails(getDocs(query(collection(db, 'programMembers'), where('uid', '==', 'memA'))));
  });

  it('coach inbox: they may read and reply to threads in their program only', async () => {
    const db = coach('coach-a');
    await assertSucceeds(getDocs(query(collection(db, 'coachInboxMessages'), where('programId', '==', 'progA'))));
    await assertFails(getDocs(query(collection(db, 'coachInboxMessages'), where('programId', '==', 'progB'))));
    await assertSucceeds(setDoc(doc(db, 'coachInboxMessages/new1'), { programId: 'progA', memberUid: 'memA', fromUid: 'coach-a', text: 'hi', createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db, 'coachInboxMessages/new2'), { programId: 'progB', memberUid: 'memB', fromUid: 'coach-a', text: 'hi', createdAt: serverTimestamp() }));
  });

  it('cannot read deletion requests, and invites are never client-writable', async () => {
    const db = coach('coach-a');
    await assertFails(getDocs(collection(db, 'deletionRequests')));
    await assertFails(setDoc(doc(db, 'programInvites/email_new@x.com'), { programId: 'progA', contact: 'new@x.com' }));
  });
});

describe('admin console queries', () => {
  it('everything an admin page lists is readable', async () => {
    const db = admin();
    await assertSucceeds(getDocs(collection(db, 'programs')));
    await assertSucceeds(getDocs(collection(db, 'programCodes')));
    await assertSucceeds(getDocs(query(collection(db, 'programInvites'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(query(collection(db, 'announcements'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(query(collection(db, 'users'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(collection(db, 'consultations')));
    await assertSucceeds(getDocs(collection(db, 'supportRequests')));
    await assertSucceeds(getDocs(query(collection(db, 'reportedMessages'), where('status', '==', 'open'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(query(collection(db, 'deletionRequests'), orderBy('createdAt', 'desc'))));
    await assertSucceeds(getDocs(query(collection(db, 'programMembers'), where('uid', '==', 'memA'))));
    await assertSucceeds(getDocs(query(collection(db, 'glucoseReadings'), where('userId', '==', 'memA'), orderBy('createdAt', 'desc'))));
  });

  it('but invites, codes-by-client and deletion requests are still not client-writable where they should not be', async () => {
    const db = admin();
    await assertFails(setDoc(doc(db, 'programInvites/email_new@x.com'), { programId: 'progA', contact: 'new@x.com' }));
    await assertFails(setDoc(doc(db, 'announcements/x'), { title: 'x' }));
    await assertFails(setDoc(doc(db, 'deletionRequests/x'), { userId: 'memA', status: 'approved' }));
  });
});
