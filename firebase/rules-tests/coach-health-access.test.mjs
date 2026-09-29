import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-nirog-bhumi',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: 'localhost',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
    await setDoc(doc(db, 'programs/progB'), { name: 'Program B', coachId: 'coach-b' });
    await setDoc(doc(db, 'users/memA'), {
      userId: 'memA', role: 'user', status: 'active', programActive: true, activeProgramId: 'progA',
    });
    await setDoc(doc(db, 'users/memB'), {
      userId: 'memB', role: 'user', status: 'active', programActive: true, activeProgramId: 'progB',
    });
    await setDoc(doc(db, 'glucoseReadings/gA'), { userId: 'memA', value: 101, createdAt: serverTimestamp() });
    await setDoc(doc(db, 'consultations/cA'), {
      userId: 'memA', status: 'pending', paymentStatus: 'pending', expertId: 'expert-a', createdAt: serverTimestamp(),
    });
    await setDoc(doc(db, 'coachNotes/nA'), {
      targetUid: 'memA', authorId: 'coach-a', text: 'private care note', createdAt: serverTimestamp(),
    });
    await setDoc(doc(db, 'programCodes/codeA'), { programId: 'progA', code: 'CODE-A', active: true });
    await setDoc(doc(db, 'programCodes/codeB'), { programId: 'progB', code: 'CODE-B', active: true });
    await setDoc(doc(db, 'expertAssignments/expert-a_memA'), { expertId: 'expert-a', userId: 'memA' });
  });
});

const user = (uid) => testEnv.authenticatedContext(uid, { role: 'user' }).firestore();
const coach = (uid) => testEnv.authenticatedContext(uid, { role: 'coach' }).firestore();
const admin = () => testEnv.authenticatedContext('admin-uid', { role: 'admin' }).firestore();
const expert = (uid) => testEnv.authenticatedContext(uid, { role: 'expert' }).firestore();

describe('assigned-coach privacy boundaries', () => {
  it('allows an owner, admin, assigned coach and assigned expert to read a health log', async () => {
    await assertSucceeds(getDoc(doc(user('memA'), 'glucoseReadings/gA')));
    await assertSucceeds(getDoc(doc(admin(), 'glucoseReadings/gA')));
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'glucoseReadings/gA')));
    await assertSucceeds(getDoc(doc(expert('expert-a'), 'glucoseReadings/gA')));
  });

  it('blocks an unassigned coach from reading another program member health log', async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'glucoseReadings/gA')));
  });

  it('keeps coach health-log access read-only', async () => {
    await assertFails(updateDoc(doc(coach('coach-a'), 'glucoseReadings/gA'), { value: 999 }));
    await assertFails(deleteDoc(doc(coach('coach-a'), 'glucoseReadings/gA')));
  });

  it('scopes user-profile reads to a coach assigned to that user program', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'users/memA')));
    await assertFails(getDoc(doc(coach('coach-b'), 'users/memA')));
  });

  it('scopes consultation reads to the assigned coach', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'consultations/cA')));
    await assertFails(getDoc(doc(coach('coach-b'), 'consultations/cA')));
  });

  it('scopes private coach notes to the assigned coach and admin', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'coachNotes/nA')));
    await assertSucceeds(getDoc(doc(admin(), 'coachNotes/nA')));
    await assertFails(getDoc(doc(coach('coach-b'), 'coachNotes/nA')));
  });

  it('allows only the assigned coach or admin to create a note for a member', async () => {
    await assertSucceeds(setDoc(doc(coach('coach-a'), 'coachNotes/newA'), {
      targetUid: 'memA', authorId: 'coach-a', text: 'assigned note', createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(coach('coach-b'), 'coachNotes/newB'), {
      targetUid: 'memA', authorId: 'coach-b', text: 'cross-program note', createdAt: serverTimestamp(),
    }));
  });

  it('scopes program access-code reads and updates to the assigned program coach', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'programCodes/codeA')));
    await assertFails(getDoc(doc(coach('coach-a'), 'programCodes/codeB')));
    await assertSucceeds(updateDoc(doc(coach('coach-a'), 'programCodes/codeA'), { active: false }));
    await assertFails(updateDoc(doc(coach('coach-a'), 'programCodes/codeB'), { active: false }));
  });
});
