const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp } = require('firebase-admin/firestore');
const { fns, db, resetFirestore, call, rejectsWithCode } = require('./helpers.cjs');

const redeem = (code, uid = 'user1') => call(fns.redeemProgramCode, { code }, uid);

describe('redeemProgramCode', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('programs/progA').set({ name: 'July Batch', durationWeeks: 6, coachId: 'coach-a', code: 'LEGACY1' });
    await db.doc('users/user1').set({ userId: 'user1', fullName: 'Asha' });
    await db.doc('users/user2').set({ userId: 'user2', fullName: 'Ravi' });
    await db.doc('users/user3').set({ userId: 'user3', fullName: 'Meera' });
  });

  it('requires sign-in', async () => {
    await rejectsWithCode(call(fns.redeemProgramCode, { code: 'X' }, null), 'unauthenticated');
  });

  it('rejects an empty code and an unknown code', async () => {
    await rejectsWithCode(redeem('  '), 'invalid-argument');
    await rejectsWithCode(redeem('NOPE1234'), 'not-found');
    await rejectsWithCode(redeem('A/B'), 'not-found');
  });

  it('enrolls with the legacy programs.code and creates the roster entry', async () => {
    const result = await redeem('legacy1');
    assert.equal(result.activeProgramId, 'progA');
    assert.equal(result.programDurationDays, 42);
    const user = (await db.doc('users/user1').get()).data();
    assert.equal(user.programActive, true);
    assert.equal(user.activeProgramId, 'progA');
    const member = (await db.doc('programMembers/progA_user1').get()).data();
    assert.equal(member.name, 'Asha');
    assert.equal(member.status, 'active');
  });

  it('enrolls with a console access code (case-insensitive) and counts the use', async () => {
    await db.doc('programCodes/JULY26').set({ code: 'JULY26', programId: 'progA', active: true, maxUses: 2, uses: 0 });
    const result = await redeem('july26');
    assert.equal(result.activeProgramName, 'July Batch');
    const code = (await db.doc('programCodes/JULY26').get()).data();
    assert.equal(code.uses, 1);
    assert.ok((await db.doc('programCodes/JULY26/redemptions/user1').get()).exists);
  });

  it('still honours a code an older console stored with mixed casing', async () => {
    await db.doc('programCodes/Summer26').set({ code: 'Summer26', programId: 'progA', active: true, maxUses: 5, uses: 0 });
    assert.equal((await redeem('Summer26')).activeProgramId, 'progA');
    assert.equal((await db.doc('programCodes/Summer26').get()).get('uses'), 1);
  });

  it('does not burn a second seat when the same member redeems again', async () => {
    await db.doc('programCodes/JULY26').set({ code: 'JULY26', programId: 'progA', active: true, maxUses: 2, uses: 0 });
    await redeem('JULY26');
    await redeem('JULY26');
    assert.equal((await db.doc('programCodes/JULY26').get()).get('uses'), 1);
  });

  it('keeps the original join date on a repeat redeem', async () => {
    await db.doc('programCodes/JULY26').set({ code: 'JULY26', programId: 'progA', active: true, uses: 0 });
    await redeem('JULY26');
    const first = (await db.doc('programMembers/progA_user1').get()).get('joinedAt');
    await redeem('JULY26');
    assert.deepEqual((await db.doc('programMembers/progA_user1').get()).get('joinedAt'), first);
  });

  it('refuses once maxUses is reached, and only counts successful redemptions', async () => {
    await db.doc('programCodes/TWO').set({ code: 'TWO', programId: 'progA', active: true, maxUses: 2, uses: 0 });
    await redeem('TWO', 'user1');
    await redeem('TWO', 'user2');
    await rejectsWithCode(redeem('TWO', 'user3'), 'resource-exhausted');
    assert.equal((await db.doc('programCodes/TWO').get()).get('uses'), 2);
    assert.notEqual((await db.doc('users/user3').get()).get('programActive'), true);
  });

  it('never over-admits when members race for the last seat', async () => {
    await db.doc('programCodes/RACE').set({ code: 'RACE', programId: 'progA', active: true, maxUses: 1, uses: 0 });
    const results = await Promise.allSettled([redeem('RACE', 'user1'), redeem('RACE', 'user2'), redeem('RACE', 'user3')]);
    assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
    assert.equal((await db.doc('programCodes/RACE').get()).get('uses'), 1);
  });

  it('treats maxUses null/0 as unlimited', async () => {
    await db.doc('programCodes/OPEN').set({ code: 'OPEN', programId: 'progA', active: true, maxUses: null, uses: 50 });
    await redeem('OPEN', 'user1');
    await redeem('OPEN', 'user2');
    assert.equal((await db.doc('programCodes/OPEN').get()).get('uses'), 52);
  });

  it('refuses an expired code and a deactivated code, without enrolling', async () => {
    await db.doc('programCodes/OLD').set({ code: 'OLD', programId: 'progA', active: true, expiresAt: Timestamp.fromMillis(Date.now() - 60_000) });
    await db.doc('programCodes/OFF').set({ code: 'OFF', programId: 'progA', active: false });
    const expired = await rejectsWithCode(redeem('OLD'), 'failed-precondition');
    assert.match(expired.message, /expired/i);
    const off = await rejectsWithCode(redeem('OFF'), 'failed-precondition');
    assert.match(off.message, /no longer active/i);
    assert.notEqual((await db.doc('users/user1').get()).get('programActive'), true);
  });

  it('accepts a code that has not expired yet', async () => {
    await db.doc('programCodes/LATER').set({ code: 'LATER', programId: 'progA', active: true, expiresAt: Timestamp.fromMillis(Date.now() + 86_400_000) });
    assert.equal((await redeem('LATER')).programActive, true);
  });

  it('rejects a code whose program no longer exists', async () => {
    await db.doc('programCodes/GHOST').set({ code: 'GHOST', programId: 'deleted-program', active: true });
    await rejectsWithCode(redeem('GHOST'), 'not-found');
  });
});
