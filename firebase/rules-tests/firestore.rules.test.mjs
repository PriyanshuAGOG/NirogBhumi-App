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

  it('lets a member opt themselves into the batch leaderboard', async () => {
    await assertSucceeds(updateDoc(doc(member('mem1'), 'programMembers/progA_mem1'), {
      leaderboardOptIn: true,
    }));
  });

  it('denies a member opting someone else into the leaderboard', async () => {
    await assertFails(updateDoc(doc(member('someone-else'), 'programMembers/progA_mem1'), {
      leaderboardOptIn: true,
    }));
  });
});

describe('coachInboxMessages (private ask-your-coach threads)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'users/mem2'), { userId: 'mem2', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'coachInboxMessages/q1'), {
        programId: 'progA', memberUid: 'mem1', fromUid: 'mem1',
        senderName: 'Member One', senderRole: 'member', text: 'Is walking after dinner okay?',
        createdAt: serverTimestamp(),
      });
    });
  });

  it('lets a member post a question to their own thread', async () => {
    await assertSucceeds(setDoc(doc(member('mem1'), 'coachInboxMessages/q2'), {
      programId: 'progA', memberUid: 'mem1', fromUid: 'mem1',
      senderName: 'Member One', senderRole: 'member', text: 'Another question',
      createdAt: serverTimestamp(),
    }));
  });

  it("denies a member posting into another member's thread", async () => {
    await assertFails(setDoc(doc(member('mem2'), 'coachInboxMessages/q3'), {
      programId: 'progA', memberUid: 'mem1', fromUid: 'mem2',
      senderName: 'Member Two', senderRole: 'member', text: 'Snooping in',
      createdAt: serverTimestamp(),
    }));
  });

  it('lets a member read their own thread', async () => {
    await assertSucceeds(getDoc(doc(member('mem1'), 'coachInboxMessages/q1')));
  });

  it("denies a batchmate reading another member's thread", async () => {
    await assertFails(getDoc(doc(member('mem2'), 'coachInboxMessages/q1')));
  });

  it("lets the program's coach read and reply to a member thread", async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'coachInboxMessages/q1')));
    await assertSucceeds(setDoc(doc(coach('coach-a'), 'coachInboxMessages/r1'), {
      programId: 'progA', memberUid: 'mem1', fromUid: 'coach-a',
      senderName: 'Coach A', senderRole: 'coach', text: 'Yes, 15 minutes is great.',
      createdAt: serverTimestamp(),
    }));
  });

  it("denies an unassigned coach reading another program's threads", async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'coachInboxMessages/q1')));
  });

  it('denies editing a sent message (immutable thread)', async () => {
    await assertFails(updateDoc(doc(member('mem1'), 'coachInboxMessages/q1'), { text: 'edited' }));
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

  it('denies a member pinning a message via the reactions-only branch', async () => {
    await assertFails(updateDoc(doc(member('mem2'), 'programChatMessages/msg1'), {
      pinned: true,
    }));
  });
});

describe('programTypingStatus ("X is typing...")', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'users/mem2'), { userId: 'mem2', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'users/mem3'), { userId: 'mem3', activeProgramId: 'progB', programActive: true });
    });
  });

  it('lets a member set their own typing status', async () => {
    await assertSucceeds(setDoc(doc(member('mem1'), 'programTypingStatus/progA_mem1'), {
      programId: 'progA', uid: 'mem1', name: 'Member One', updatedAt: serverTimestamp(),
    }));
  });

  it("denies setting someone else's typing status", async () => {
    await assertFails(setDoc(doc(member('mem1'), 'programTypingStatus/progA_mem2'), {
      programId: 'progA', uid: 'mem2', name: 'Member Two', updatedAt: serverTimestamp(),
    }));
  });

  it('denies a member setting typing status for a program they are not active in', async () => {
    await assertFails(setDoc(doc(member('mem3'), 'programTypingStatus/progA_mem3'), {
      programId: 'progA', uid: 'mem3', name: 'Member Three', updatedAt: serverTimestamp(),
    }));
  });

  it('lets a batchmate read another member\'s typing status', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programTypingStatus/progA_mem1'), {
        programId: 'progA', uid: 'mem1', name: 'Member One', updatedAt: serverTimestamp(),
      });
    });
    await assertSucceeds(getDoc(doc(member('mem2'), 'programTypingStatus/progA_mem1')));
  });

  it('lets a member delete their own typing status doc', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programTypingStatus/progA_mem1'), {
        programId: 'progA', uid: 'mem1', name: 'Member One', updatedAt: serverTimestamp(),
      });
    });
    await assertSucceeds(deleteDoc(doc(member('mem1'), 'programTypingStatus/progA_mem1')));
  });

  it("denies deleting someone else's typing status doc", async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programTypingStatus/progA_mem1'), {
        programId: 'progA', uid: 'mem1', name: 'Member One', updatedAt: serverTimestamp(),
      });
    });
    await assertFails(deleteDoc(doc(member('mem2'), 'programTypingStatus/progA_mem1')));
  });
});

describe('programChatMessages pin toggle (staff-only, per-program)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'programChatMessages/msg1'), {
        programId: 'progA', userId: 'mem1', text: 'Walk at 6pm today', createdAt: serverTimestamp(),
      });
    });
  });

  it('lets the assigned coach pin a message', async () => {
    await assertSucceeds(updateDoc(doc(coach('coach-a'), 'programChatMessages/msg1'), {
      pinned: true, pinnedBy: 'coach-a', pinnedAt: serverTimestamp(),
    }));
  });

  it('lets admin pin a message regardless of coachId', async () => {
    await assertSucceeds(updateDoc(doc(admin(), 'programChatMessages/msg1'), {
      pinned: true, pinnedBy: 'admin-uid', pinnedAt: serverTimestamp(),
    }));
  });

  it('denies an unassigned coach pinning a message in another program', async () => {
    await assertFails(updateDoc(doc(coach('coach-b'), 'programChatMessages/msg1'), {
      pinned: true, pinnedBy: 'coach-b', pinnedAt: serverTimestamp(),
    }));
  });

  it('denies a member (even the author) pinning their own message', async () => {
    await assertFails(updateDoc(doc(member('mem1'), 'programChatMessages/msg1'), {
      pinned: true, pinnedBy: 'mem1', pinnedAt: serverTimestamp(),
    }));
  });

  it('denies a coach smuggling a text edit in through the pin branch', async () => {
    await assertFails(updateDoc(doc(coach('coach-a'), 'programChatMessages/msg1'), {
      pinned: true, text: 'rewritten by staff',
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

describe('users/{uid} program-field lock also applies on create (not just update)', () => {
  it('denies a brand-new user creating their own doc with programActive included', async () => {
    await assertFails(setDoc(doc(member('newmem'), 'users/newmem'), {
      userId: 'newmem', programActive: true, activeProgramId: 'progA',
    }));
  });

  it('denies a brand-new user creating their own doc with a forged status', async () => {
    await assertFails(setDoc(doc(member('newmem'), 'users/newmem'), {
      userId: 'newmem', status: 'active',
    }));
  });

  it('denies a brand-new user creating their own doc with the admin role', async () => {
    await assertFails(setDoc(doc(member('newmem'), 'users/newmem'), {
      userId: 'newmem', role: 'admin',
    }));
  });

  it('still lets a brand-new user create their own doc without those fields', async () => {
    await assertSucceeds(setDoc(doc(member('newmem'), 'users/newmem'), {
      userId: 'newmem', fullName: 'New Member',
    }));
  });
});

describe('health-log collection group - coach read scoped to assigned members', () => {
  // mem1 is in progA (coach-a's batch); coach-b runs a different batch and
  // has no relationship to mem1 - the core PII-scoping check is that
  // coach-b can NOT read mem1's health data.
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
      await setDoc(doc(db, 'programs/progB'), { name: 'Program B', coachId: 'coach-b' });
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'glucoseReadings/r1'), {
        userId: 'mem1', value: 110, createdAt: serverTimestamp(),
      });
    });
  });

  it('lets the owner read their own reading', async () => {
    await assertSucceeds(getDoc(doc(member('mem1'), 'glucoseReadings/r1')));
  });

  it("lets the member's assigned coach read it", async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'glucoseReadings/r1')));
  });

  it("denies a coach who is NOT assigned to the member's program", async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'glucoseReadings/r1')));
  });

  it('lets admin read any reading', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'glucoseReadings/r1')));
  });

  it('denies an unrelated signed-in member', async () => {
    await assertFails(getDoc(doc(member('mem2'), 'glucoseReadings/r1')));
  });

  it('denies an unauthenticated caller', async () => {
    await assertFails(getDoc(doc(anon(), 'glucoseReadings/r1')));
  });

  it("denies even the assigned coach deleting a member's reading (admin-only)", async () => {
    await assertFails(deleteDoc(doc(coach('coach-a'), 'glucoseReadings/r1')));
  });

  it('lets admin delete a reading', async () => {
    await assertSucceeds(deleteDoc(doc(admin(), 'glucoseReadings/r1')));
  });
});

describe('coachNotes - scoped to the member\'s assigned coach', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
      await setDoc(doc(db, 'users/mem1'), { userId: 'mem1', activeProgramId: 'progA', programActive: true });
      await setDoc(doc(db, 'coachNotes/existing'), {
        targetUid: 'mem1', authorId: 'coach-a', text: 'Baseline', createdAt: serverTimestamp(),
      });
    });
  });

  it("lets the member's assigned coach create a note authored by themselves", async () => {
    await assertSucceeds(setDoc(doc(coach('coach-a'), 'coachNotes/note1'), {
      targetUid: 'mem1', authorId: 'coach-a', text: 'Doing well', createdAt: serverTimestamp(),
    }));
  });

  it('denies an unassigned coach creating a note on that member', async () => {
    await assertFails(setDoc(doc(coach('coach-b'), 'coachNotes/note1'), {
      targetUid: 'mem1', authorId: 'coach-b', text: 'Snooping', createdAt: serverTimestamp(),
    }));
  });

  it('denies creating a note claiming to be authored by someone else', async () => {
    await assertFails(setDoc(doc(coach('coach-a'), 'coachNotes/note1'), {
      targetUid: 'mem1', authorId: 'someone-else', text: 'x', createdAt: serverTimestamp(),
    }));
  });

  it("lets the assigned coach read a note on their member", async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'coachNotes/existing')));
  });

  it('denies an unassigned coach reading a note on that member', async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'coachNotes/existing')));
  });
});

describe('appUpdates (self-update system release metadata)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'appUpdates/production'), {
        channel: 'production', latestVersionCode: 42, latestVersionName: '1.0.42',
        minSupportedVersionCode: 30, apkUrl: 'https://example.com/app.apk',
        checksum: 'abc123', forceUpdate: false, releaseNotes: 'Bug fixes',
      });
    });
  });

  it('lets an anonymous (signed-out) caller read release metadata', async () => {
    await assertSucceeds(getDoc(doc(anon(), 'appUpdates/production')));
  });

  it('lets a plain signed-in member read release metadata', async () => {
    await assertSucceeds(getDoc(doc(member('mem1'), 'appUpdates/production')));
  });

  it('denies a plain member writing release metadata', async () => {
    await assertFails(setDoc(doc(member('mem1'), 'appUpdates/production'), {
      channel: 'production', latestVersionCode: 999, latestVersionName: '9.9.9',
    }));
  });

  it('lets admin write release metadata', async () => {
    await assertSucceeds(setDoc(doc(admin(), 'appUpdates/production'), {
      channel: 'production', latestVersionCode: 43, latestVersionName: '1.0.43',
      minSupportedVersionCode: 30, apkUrl: 'https://example.com/app43.apk',
      checksum: 'def456', forceUpdate: false, releaseNotes: 'More fixes',
    }));
  });
});

// programInvites (pre-enrollment by phone/email) holds contact info for
// people who haven't signed up yet - it's written/read only via the
// inviteToProgram/revokeInvite callables and onUserCreate's auto-consume
// (both under the Admin SDK, which bypasses rules entirely), so the client
// path through firestore.rules must stay fully closed - no explicit rule
// exists for this collection, relying on the trailing default-deny match.
describe('programInvites (staff-only via callable, never client-writable)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programInvites/email_member@example.com'), {
        contact: 'member@example.com', contactType: 'email', programId: 'prog1',
        programName: 'Test Program', consumedAt: null,
      });
    });
  });

  it('denies an anonymous caller reading an invite', async () => {
    await assertFails(getDoc(doc(anon(), 'programInvites/email_member@example.com')));
  });

  it('denies a plain member reading an invite', async () => {
    await assertFails(getDoc(doc(member('mem1'), 'programInvites/email_member@example.com')));
  });

  it('denies an admin writing an invite directly (must go through inviteToProgram)', async () => {
    await assertFails(setDoc(doc(admin(), 'programInvites/email_other@example.com'), {
      contact: 'other@example.com', contactType: 'email', programId: 'prog1',
    }));
  });
});

describe('announcements (staff-only source doc, never client-writable)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'announcements/ann1'), {
        title: 'Test', body: 'Body', authorId: 'admin-uid',
        audience: { scope: 'all_users', programIds: [], inactiveDays: 4 },
        recipientUids: ['mem1'],
      });
    });
  });

  it('lets an admin read the source announcement', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'announcements/ann1')));
  });

  it('lets a coach read the source announcement', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'announcements/ann1')));
  });

  it('denies a plain member reading the source announcement (only their own fan-out copy)', async () => {
    await assertFails(getDoc(doc(member('mem1'), 'announcements/ann1')));
  });

  it('denies any client, including admin, writing an announcement directly (must go through createAnnouncement)', async () => {
    await assertFails(setDoc(doc(admin(), 'announcements/ann2'), { title: 'x', body: 'y' }));
  });

  it('denies any client deleting an announcement directly (must go through deleteAnnouncement)', async () => {
    await assertFails(deleteDoc(doc(admin(), 'announcements/ann1')));
  });
});

describe('users/{uid}/announcements fan-out copy (own-only, never client-writable)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users/mem1/announcements/ann1'), {
        announcementId: 'ann1', title: 'Test', body: 'Body',
      });
    });
  });

  it('lets the recipient read their own fan-out copy', async () => {
    await assertSucceeds(getDoc(doc(member('mem1'), 'users/mem1/announcements/ann1')));
  });

  it('denies a different member reading someone else\'s fan-out copy', async () => {
    await assertFails(getDoc(doc(member('mem2'), 'users/mem1/announcements/ann1')));
  });

  it('denies the recipient writing their own fan-out copy directly', async () => {
    await assertFails(setDoc(doc(member('mem1'), 'users/mem1/announcements/ann2'), { title: 'x' }));
  });

  it('denies an admin writing a fan-out copy directly (must go through createAnnouncement)', async () => {
    await assertFails(setDoc(doc(admin(), 'users/mem1/announcements/ann2'), { title: 'x' }));
  });
});

describe('dataExportRequests/deletionRequests (callable-only, never client-writable)', () => {
  it('denies a member creating a dataExportRequests doc directly (must go through requestDataExport)', async () => {
    await assertFails(setDoc(doc(member('mem1'), 'dataExportRequests/req1'), {
      userId: 'mem1', status: 'requested', createdAt: serverTimestamp(),
    }));
  });

  it('denies a member creating a deletionRequests doc directly (must go through requestAccountDeletion)', async () => {
    await assertFails(setDoc(doc(member('mem1'), 'deletionRequests/req1'), {
      userId: 'mem1', status: 'requested', createdAt: serverTimestamp(),
    }));
  });

  it('still lets the owner read their own request once it exists', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'dataExportRequests/req1'), { userId: 'mem1', status: 'requested' });
    });
    await assertSucceeds(getDoc(doc(member('mem1'), 'dataExportRequests/req1')));
  });
});

describe('errorReports (admin-only, matching the console route gating)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'errorReports/err1'), { userId: 'mem1', message: 'boom', resolved: false });
    });
  });

  it('denies a coach reading error telemetry', async () => {
    await assertFails(getDoc(doc(coach('coach-a'), 'errorReports/err1')));
  });

  it('lets an admin read error telemetry', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'errorReports/err1')));
  });
});

describe('programCodes (per-program staff scoping)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'programs/progA'), { name: 'Program A', coachId: 'coach-a' });
      await setDoc(doc(db, 'programs/progB'), { name: 'Program B', coachId: 'coach-b' });
      await setDoc(doc(db, 'programCodes/CODEA'), { code: 'CODEA', programId: 'progA', active: true });
    });
  });

  it('lets the assigned coach read their own program code', async () => {
    await assertSucceeds(getDoc(doc(coach('coach-a'), 'programCodes/CODEA')));
  });

  it('denies a different coach reading that code', async () => {
    await assertFails(getDoc(doc(coach('coach-b'), 'programCodes/CODEA')));
  });

  it('denies a different coach creating a code for a program they do not own', async () => {
    await assertFails(setDoc(doc(coach('coach-b'), 'programCodes/CODEB'), {
      code: 'CODEB', programId: 'progA', active: true,
    }));
  });

  it('lets admin read any program code', async () => {
    await assertSucceeds(getDoc(doc(admin(), 'programCodes/CODEA')));
  });
});

describe('consentReceipts (DPDP immutable consent record)', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'users/mem1/consentReceipts/seed'), {
        version: '2025-07', purposes: { healthData: true }, acceptedAt: serverTimestamp(),
      });
    });
  });

  it('lets the owner append a consent receipt stamped with the server time', async () => {
    await assertSucceeds(setDoc(doc(member('mem1'), 'users/mem1/consentReceipts/r1'), {
      version: '2025-07', purposes: { healthData: true, marketing: false }, acceptedAt: serverTimestamp(),
    }));
  });

  it('rejects a back-dated (non-server-time) acceptedAt', async () => {
    await assertFails(setDoc(doc(member('mem1'), 'users/mem1/consentReceipts/r2'), {
      version: '2025-07', purposes: { healthData: true }, acceptedAt: new Date('2020-01-01'),
    }));
  });

  it("forbids writing a receipt under another user's path", async () => {
    await assertFails(setDoc(doc(member('mem2'), 'users/mem1/consentReceipts/r3'), {
      version: '2025-07', purposes: { healthData: true }, acceptedAt: serverTimestamp(),
    }));
  });

  it('lets the owner and an admin read receipts, but not another member', async () => {
    await assertSucceeds(getDoc(doc(member('mem1'), 'users/mem1/consentReceipts/seed')));
    await assertSucceeds(getDoc(doc(admin(), 'users/mem1/consentReceipts/seed')));
    await assertFails(getDoc(doc(member('mem2'), 'users/mem1/consentReceipts/seed')));
  });

  it('is immutable - no update or delete', async () => {
    await assertFails(updateDoc(doc(member('mem1'), 'users/mem1/consentReceipts/seed'), { version: 'hacked' }));
    await assertFails(deleteDoc(doc(member('mem1'), 'users/mem1/consentReceipts/seed')));
  });
});

// Sanity check that the suite itself is wired up, independent of rules content.
describe('test harness sanity', () => {
  it('ran at least one assertion', () => assert.ok(true));
});
