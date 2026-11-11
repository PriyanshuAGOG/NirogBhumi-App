// The base account record must exist for every signed-in person, whichever route created it (the sign-up trigger or this call).
const { test, before } = require('node:test');
const assert = require('node:assert/strict');
const { getAuth } = require('firebase-admin/auth');
const { fns, db, resetFirestore, call } = require('./helpers.cjs');

before(resetFirestore);

async function authUser(uid, props = {}) {
  try { await getAuth().deleteUser(uid); } catch { /* not there yet */ }
  return getAuth().createUser({ uid, ...props });
}

test('a new account gets its base record, with the real sign-up time and the contact it signed up with', async () => {
  await authUser('newbie1', { phoneNumber: '+919876500001' });
  const result = await call(fns.ensureUserProfile, {}, 'newbie1');
  assert.deepEqual(result, { created: true });
  const d = (await db.doc('users/newbie1').get()).data();
  assert.equal(d.userId, 'newbie1');
  assert.equal(d.phone, '+919876500001');
  assert.equal(d.role, 'user');
  assert.equal(d.status, 'active');
  assert.equal(d.timezone, 'Asia/Kolkata');
  assert.equal(d.notificationPreferences.maxHealthReminders, 3);
  assert.ok(d.createdAt, 'created time is set');
});

test('an account the client already started (a profile with no role or status) is completed, keeping what it wrote', async () => {
  await authUser('half1', { email: 'half@example.org' });
  await db.doc('users/half1').set({ userId: 'half1', fullName: 'Half Done', city: 'Pune' });
  assert.deepEqual(await call(fns.ensureUserProfile, {}, 'half1'), { created: true });
  const d = (await db.doc('users/half1').get()).data();
  assert.equal(d.fullName, 'Half Done');
  assert.equal(d.city, 'Pune');
  assert.equal(d.role, 'user');
  assert.equal(d.status, 'active');
  assert.equal(d.email, 'half@example.org');
});

test('running it again changes nothing, and never undoes a role, a suspension or a preference', async () => {
  await authUser('coach1', { email: 'coach@example.org' });
  await db.doc('users/coach1').set({ userId: 'coach1', role: 'coach', status: 'suspended', email: 'coach@example.org', timezone: 'Asia/Dubai',
    notificationPreferences: { quietHoursStart: '22:00', quietHoursEnd: '06:00', maxHealthReminders: 1 }, createdAt: new Date('2026-01-01T00:00:00Z') });
  assert.deepEqual(await call(fns.ensureUserProfile, {}, 'coach1'), { created: false });
  const d = (await db.doc('users/coach1').get()).data();
  assert.equal(d.role, 'coach');
  assert.equal(d.status, 'suspended');
  assert.equal(d.timezone, 'Asia/Dubai');
  assert.equal(d.notificationPreferences.maxHealthReminders, 1);
});

test('a half-made record that is missing only the base fields is completed without changing role or status that exist', async () => {
  await authUser('part1', { email: 'part@example.org' });
  await db.doc('users/part1').set({ userId: 'part1', role: 'coach', email: 'part@example.org' });
  await call(fns.ensureUserProfile, {}, 'part1');
  const d = (await db.doc('users/part1').get()).data();
  assert.equal(d.role, 'coach');
  assert.equal(d.status, 'active');
});

test('a pre-invited member is enrolled in their batch the first time the record is made', async () => {
  await db.doc('programs/pInv').set({ name: 'July Batch', durationWeeks: 6 });
  await db.doc('programInvites/phone_+919876500009').set({ programId: 'pInv', programName: 'July Batch', consumedAt: null });
  await authUser('invited1', { phoneNumber: '+919876500009' });
  await call(fns.ensureUserProfile, {}, 'invited1');
  const d = (await db.doc('users/invited1').get()).data();
  assert.equal(d.programActive, true);
  assert.equal(d.activeProgramId, 'pInv');
  assert.ok((await db.doc('programInvites/phone_+919876500009').get()).get('consumedAt'), 'the invite is used up');
});

test('it only ever acts on the caller, and signed-out callers are refused', async () => {
  await authUser('someone-else', { email: 'else@example.org' });
  await call(fns.ensureUserProfile, { uid: 'someone-else' }, 'newbie1');   // a uid in the payload is ignored
  assert.equal((await db.doc('users/someone-else').get()).exists, false);
  await assert.rejects(call(fns.ensureUserProfile, {}, null), e => e.code === 'unauthenticated');
});
