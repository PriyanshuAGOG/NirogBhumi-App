import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where, serverTimestamp } from 'firebase/firestore';

let testEnv;
before(async () => {
  testEnv = await initializeTestEnvironment({ projectId: 'demo-nirog-bhumi', firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: 'localhost', port: 8080 } });
});
after(async () => { await testEnv.cleanup(); });
beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'programs/progA'), { name: 'A', coachId: 'coach-a' });
    await setDoc(doc(db, 'programs/progB'), { name: 'B', coachId: 'coach-b' });
    await setDoc(doc(db, 'users/memA'), { userId: 'memA', programActive: true, activeProgramId: 'progA' });
    await setDoc(doc(db, 'users/memB'), { userId: 'memB', programActive: true, activeProgramId: 'progB' });
    await setDoc(doc(db, 'users/loner'), { userId: 'loner' });
    await setDoc(doc(db, 'programResources/r1'), { programId: 'progA', category: 'diet', title: 'Week 1 plate', body: 'Half plate vegetables', createdBy: 'coach-a' });
    await setDoc(doc(db, 'programResources/r2'), { programId: 'progB', category: 'yoga', title: 'Morning routine', body: 'Cat-cow', createdBy: 'coach-b' });
  });
});

const as = (uid, role) => testEnv.authenticatedContext(uid, role ? { role } : {}).firestore();
const fresh = (over = {}) => ({ programId: 'progA', category: 'yoga', title: 'Gentle morning flow', body: 'Ten minutes, stop if dizzy.', createdBy: 'coach-a', createdByName: 'Coach A', createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...over });

describe('programResources - reading', () => {
  it('members read their own batch\'s resources (list and single) and nobody else\'s', async () => {
    const db = as('memA');
    await assertSucceeds(getDocs(query(collection(db, 'programResources'), where('programId', '==', 'progA'))));
    await assertSucceeds(getDoc(doc(db, 'programResources/r1')));
    await assertFails(getDoc(doc(db, 'programResources/r2')));
    await assertFails(getDocs(query(collection(db, 'programResources'), where('programId', '==', 'progB'))));
    await assertFails(getDocs(collection(db, 'programResources')));
  });
  it('a signed-in user with no batch, and a signed-out user, read nothing', async () => {
    await assertFails(getDoc(doc(as('loner'), 'programResources/r1')));
    await assertFails(getDoc(doc(testEnv.unauthenticatedContext().firestore(), 'programResources/r1')));
  });
  it('coaches read only their own batch; admins read all', async () => {
    await assertSucceeds(getDocs(query(collection(as('coach-a', 'coach'), 'programResources'), where('programId', '==', 'progA'))));
    await assertFails(getDoc(doc(as('coach-a', 'coach'), 'programResources/r2')));
    await assertSucceeds(getDocs(collection(as('admin1', 'admin'), 'programResources')));
  });
});

describe('programResources - writing', () => {
  it('the batch\'s coach and admins can create; members and other coaches cannot', async () => {
    await assertSucceeds(setDoc(doc(as('coach-a', 'coach'), 'programResources/n1'), fresh()));
    await assertSucceeds(setDoc(doc(as('admin1', 'admin'), 'programResources/n2'), fresh({ createdBy: 'admin1' })));
    await assertFails(setDoc(doc(as('memA'), 'programResources/n3'), fresh({ createdBy: 'memA' })));
    await assertFails(setDoc(doc(as('coach-b', 'coach'), 'programResources/n4'), fresh({ createdBy: 'coach-b' })));
  });
  it('the author field cannot be forged and the timestamp must be server time', async () => {
    await assertFails(setDoc(doc(as('coach-a', 'coach'), 'programResources/n5'), fresh({ createdBy: 'someone-else' })));
    await assertFails(setDoc(doc(as('coach-a', 'coach'), 'programResources/n6'), fresh({ createdAt: new Date(0) })));
  });
  it('rejects malformed content: bad category, empty/oversized text, non-http links, extra fields', async () => {
    const db = as('coach-a', 'coach');
    await assertFails(setDoc(doc(db, 'programResources/b1'), fresh({ category: 'astrology' })));
    await assertFails(setDoc(doc(db, 'programResources/b2'), fresh({ title: '' })));
    await assertFails(setDoc(doc(db, 'programResources/b3'), fresh({ title: 'x'.repeat(121) })));
    await assertFails(setDoc(doc(db, 'programResources/b4'), fresh({ body: 'x'.repeat(5001) })));
    await assertFails(setDoc(doc(db, 'programResources/b5'), fresh({ link: 'javascript:alert(1)' })));
    await assertFails(setDoc(doc(db, 'programResources/b6'), fresh({ isAdmin: true })));
    await assertFails(setDoc(doc(db, 'programResources/b7'), fresh({ weekNumber: -1 })));
    await assertSucceeds(setDoc(doc(db, 'programResources/ok1'), fresh({ link: 'https://nirogbhumi.com/yoga', weekNumber: 3, notify: true })));
  });
  it('coaches can edit and delete their own batch\'s resources but cannot move one to another batch', async () => {
    const db = as('coach-a', 'coach');
    await assertSucceeds(updateDoc(doc(db, 'programResources/r1'), { title: 'Week 1 plate (updated)', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'programResources/r1'), { programId: 'progB' }));
    await assertFails(updateDoc(doc(db, 'programResources/r2'), { title: 'hijack' }));
    await assertSucceeds(deleteDoc(doc(db, 'programResources/r1')));
    await assertFails(deleteDoc(doc(db, 'programResources/r2')));
  });
  it('members can never edit or delete a resource', async () => {
    await assertFails(updateDoc(doc(as('memA'), 'programResources/r1'), { title: 'x' }));
    await assertFails(deleteDoc(doc(as('memA'), 'programResources/r1')));
  });
});
