// Firestore security rules unit tests. Run only against the local emulator
// (initializeTestEnvironment refuses to talk to production). This exists
// because CI's "firebase-rules" job only ever validated that the rules file
// *compiles* - it never verified the actual access-control logic, which is
// exactly where the per-coach batch scoping added this session (programStaff())
// and a couple of field-restricted self-update rules could have silently been
// wrong with no way to notice. Covers the highest-risk paths, not every rule.
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
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
});

async function seed(fn) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

const admin = () => testEnv.authenticatedContext('admin-uid', { role: 'admin' }).firestore();
const coach = (uid) => testEnv.authenticatedContext(uid, { role: 'coach' }).firestore();
const member = (uid) => testEnv.authenticatedContext(uid, { role: 'user' }).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

describe('programStaff() per-coach batch scoping', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
      await setDoc(doc(db, 'programs/progB'), { name: 'Program B', coachId: 'coach-b' });
      await setDoc(doc(db, 'programEvents/evtA'), { programId: 'progA', title: 'Walk', createdBy: 'coach-a' });
      await setDoc(doc(db, 'programMembers/progA_mem1'), { programId: 'progA', uid: 'mem1', name: 'Member One' });
    });
  });

  it('lets the assigned coach read their own program', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'programs/progA')));
  });

  it('denies a coach reading a program they do not manage', async () => {
    await assertFails(getDoc(doc(coach('coach-a'), 'programs/progB')));
  });

  it('lets admin read any program regardless of coachId', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'programs/progA')));
    await assertSucceeds(getDoc(doc(admin(), 'programs/progB')));
  });

  it('lets the assigned coach read that program\'s events', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'programEvents/evtA')));
  });

  it('denies an unassigned coach reading another program\'s events', async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'programEvents/evtA')));
  });

  it('denies an unassigned coach reading another program\'s roster', async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'programMembers/progA_mem1')));
  });

  it('lets the assigned coach create an event for their program', async () => {
    await assertSucceeds(setDoc(doc(coach('coach-a'), 'programEvents/evtNew'), {
      programId: 'progA', title: 'New session', createdBy: 'coach-a',
    }));
  });

  it('denies an unassigned coach creating an event for a program they do not manage', async () => {
    await assertFails(setDoc(doc(coach('coach-b'), 'programEvents/evtNew'), {
      programId: 'progA', title: 'Sneaky session', createdBy: 'coach-b',
    }));
  });

  it('denies a coach reassigning a program to themselves via update', async () => {
    await assertFails(updateDoc(doc(coach('coach-b'), 'programs/progA'), { coachId: 'coach-b' }));
  });
});

describe('programMembers self-update (unread badge read markers)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programMembers/progA_mem1'), {
        programId: 'progA', uid: 'mem1', name: 'Member One', contributionKm: 12,
      });
    });
  });

  it('lets a member set their own lastReadGeneralAt', async () => {
    await assertSucceeds(updateDoc(doc(member('mem1'), 'programMembers/progA_mem1'), {
      lastReadGeneralAt: serverTimestamp(),
    }));
  });

  it('denies a member touching any other field on their own roster doc', async () => {
    await assertFails(updateDoc(doc(member('mem1'), 'programMembers/progA_mem1'), {
      contributionKm: 9999,
    }));
  });

  it('denies a member updating read markers on someone else\'s roster doc', async () => {
    await assertFails(updateDoc(doc(member('someone-else'), 'programMembers/progA_mem1'), {
      lastReadGeneralAt: serverTimestamp(),
    }));
  });
});

describe('programChatMessages reactions-only update', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'users/mem2'), { userId: 'mem2', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'programChatMessages/msg1'), {
        programId: 'progA', userId: 'mem1', text: 'hello', createdAt: serverTimestamp(),
      });
    });
  });

  it('lets a batchmate add a reaction', async () => {
    await assertSucceeds(updateDoc(doc(member('mem2'), 'programChatMessages/msg1'), {
      reactions: { '👍': ['mem2'] },
    }));
  });

  it('denies a batchmate editing message text via the same call', async () => {
    await assertFails(updateDoc(doc(member('mem2'), 'programChatMessages/msg1'), {
      text: 'edited by someone else',
    }));
  });
});

describe('users/{uid} program-field lock (self-enrollment bypass fix)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users/mem1'), {
        userId: 'mem1', role: 'user', status: 'active', programActive: false,
      });
    });
  });

  it('denies a user granting themselves programActive/activeProgramId directly', async () => {
    await assertFails(updateDoc(doc(member('mem1'), 'users/mem1'), {
      programActive: true, activeProgramId: 'progA',
    }));
  });

  it('denies a user granting themselves the admin role', async () => {
    await assertFails(updateDoc(doc(member('mem1'), 'users/mem1'), { role: 'admin' }));
  });

  it('still lets a user update an unrelated field like fullName', async () => {
    await assertSucceeds(updateDoc(doc(member('mem1'), 'users/mem1'), { fullName: 'New Name' }));
  });

  it('lets a user set their own checkinHourHint (smart reminder timing)', async () => {
    await assertSucceeds(updateDoc(doc(member('mem1'), 'users/mem1'), {
      checkinHourHint: 19, lastCheckinAt: serverTimestamp(),
    }));
  });
});

describe('health-log collection group (glucoseReadings as representative)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'glucoseReadings/r1'), {
        userId: 'mem1', value: 110, createdAt: serverTimestamp(),
      });
    });
  });

  it('lets the owner read their own reading', async () => {
    await assertSucceeds(getDoc(doc(member('mem1'), 'glucoseReadings/r1')));
  });

  it('lets any staff (coach or admin) read - not yet program-scoped, by design', async () => {
    await assertSucceeds(getDoc(doc(coach('any-coach'), 'glucoseReadings/r1')));
  });

  it('denies an unrelated signed-in member', async () => {
    await assertFails(getDoc(doc(member('mem2'), 'glucoseReadings/r1')));
  });

  it('denies an unauthenticated caller', async () => {
    await assertFails(getDoc(doc(anon(), 'glucoseReadings/r1')));
  });

  it('denies a coach deleting another member\'s reading (admin-only)', async () => {
    await assertFails(deleteDoc(doc(coach('any-coach'), 'glucoseReadings/r1')));
  });

  it('lets admin delete a reading', async () => {
    await assertSucceeds(deleteDoc(doc(admin(), 'glucoseReadings/r1')));
  });
});

describe('coachNotes stay staff-wide (documented, not per-program scoped)', () => {
  it('lets any coach create a note with themselves as author', async () => {
    const db = coach('coach-x');
    await assertSucceeds(setDoc(doc(db, 'coachNotes/note1'), {
      targetUid: 'mem1', authorId: 'coach-x', text: 'Doing well', createdAt: serverTimestamp(),
    }));
  });

  it('denies creating a note claiming to be authored by someone else', async () => {
    const db = coach('coach-x');
    await assertFails(setDoc(doc(db, 'coachNotes/note1'), {
      targetUid: 'mem1', authorId: 'someone-else', text: 'Doing well', createdAt: serverTimestamp(),
    }));
  });
});

// Sanity check that the suite itself is wired up, independent of rules content.
describe('test harness sanity', () => {
  it('ran at least one assertion', () => assert.ok(true));
});
