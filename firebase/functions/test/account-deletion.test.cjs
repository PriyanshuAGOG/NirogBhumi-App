const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { fns, db, resetFirestore, call, rejectsWithCode } = require('./helpers.cjs');
const deletion = require('../lib/accountDeletion.js');

const DAY = 86_400_000;
const NOW = Date.now();

function makeDeps(overrides = {}) {
  const deps = {
    db,
    files: [],
    deletedAuth: [],
    deleteFiles: async prefix => { deps.files.push(prefix); },
    deleteAuthUser: async uid => { deps.deletedAuth.push(uid); },
    ...overrides,
  };
  return deps;
}

async function seedMember(uid, { research = false } = {}) {
  await db.doc(`users/${uid}`).set({ userId: uid, fullName: `Member ${uid}`, email: `${uid}@example.com`, activeProgramId: 'progA', programActive: true, consent: { healthData: true, research } });
  await db.doc(`users/${uid}/announcements/a1`).set({ title: 'hi' });
  await db.doc(`users/${uid}/consentReceipts/c1`).set({ version: '2025-07' });
  for (const name of ['glucoseReadings', 'bpReadings', 'sleepLogs', 'walkLogs', 'weightLogs', 'medicationLogs', 'checklistLogs', 'dailyCheckins']) {
    await db.doc(`${name}/${uid}-1`).set({ userId: uid, profileId: uid, value: 100 });
  }
  await db.doc(`labReports/${uid}-1`).set({ userId: uid, labName: 'City Labs', notes: 'HbA1c', fileUrl: 'gs://x', value: 6.1 });
  await db.doc(`profiles/${uid}-p`).set({ userId: uid, name: 'Family' });
  await db.doc(`notifications/${uid}-n`).set({ userId: uid, title: 'x' });
  await db.doc(`supportRequests/${uid}-s`).set({ userId: uid, status: 'open' });
  await db.doc(`errorReports/${uid}-e`).set({ userId: uid });
  await db.doc(`programChatMessages/${uid}-m`).set({ userId: uid, programId: 'progA', text: 'hello' });
  await db.doc(`programChatMessages/other-m-${uid}`).set({ userId: 'someone-else', programId: 'progA', text: 'keep me' });
  await db.doc(`programTypingStatus/progA_${uid}`).set({ uid, programId: 'progA' });
  await db.doc(`coachInboxMessages/${uid}-i`).set({ memberUid: uid, programId: 'progA', text: 'q' });
  await db.doc(`coachMessages/${uid}-c`).set({ toUid: uid, programId: 'progA', text: 'note' });
  await db.doc(`coachNotes/${uid}-n`).set({ targetUid: uid, text: 'private' });
  await db.doc(`programMembers/progA_${uid}`).set({ uid, programId: 'progA', name: uid });
  await db.doc(`orders/${uid}-o`).set({ userId: uid, name: 'Asha', address: '1 Road', phone: '999', total: 499, paymentStatus: 'paid' });
  await db.doc(`reportedMessages/${uid}-r1`).set({ reporterId: uid, reportedUserId: 'x', reportedText: 'their words', programId: 'progA' });
  await db.doc(`reportedMessages/${uid}-r2`).set({ reporterId: 'y', reportedUserId: uid, reportedText: 'my words', programId: 'progA' });
  await db.doc(`programInvites/${uid}-inv`).set({ contact: `${uid}@example.com`, programId: 'progA' });
  await db.doc(`programInvites/${uid}-used`).set({ contact: 'someone-else@example.com', programId: 'progA', consumedByUid: uid });
  await db.doc('announcements/ann1').set({ title: 'Batch news', recipientUids: [uid, 'other'] });
  await db.doc('batchStats/progA_2026-09-29').set({ programId: 'progA' });
  await db.doc(`batchStats/progA_2026-09-29/checkedInMembers/${uid}`).set({ at: 1 });
}

const exists = async path => (await db.doc(path).get()).exists;

describe('scheduling and cancelling', () => {
  beforeEach(resetFirestore);

  it('schedules deletion after the grace period and is idempotent while pending', async () => {
    const first = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    assert.equal(first.alreadyPending, false);
    assert.equal(first.scheduledFor.toMillis(), NOW + deletion.DELETION_GRACE_DAYS * DAY);
    const again = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW + 1000);
    assert.equal(again.alreadyPending, true);
    assert.equal(again.requestId, first.requestId);
    assert.equal((await db.collection('deletionRequests').get()).size, 1);
  });

  it('lets a member cancel during the grace period and schedule again later', async () => {
    const first = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    assert.equal(await deletion.cancelAccountDeletion(db, 'u1'), true);
    assert.equal((await db.doc(`deletionRequests/${first.requestId}`).get()).get('status'), 'cancelled');
    assert.equal(await deletion.cancelAccountDeletion(db, 'u1'), false);
    const second = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    assert.equal(second.alreadyPending, false);
    assert.notEqual(second.requestId, first.requestId);
  });

  it('the callables schedule, report the schedule, and cancel', async () => {
    await db.doc('users/u1').set({ userId: 'u1' });
    const res = await call(fns.requestAccountDeletion, {}, 'u1');
    assert.equal(res.accepted, true);
    assert.equal(res.alreadyPending, false);
    assert.equal(res.graceDays, 7);
    assert.ok(res.scheduledForMillis > Date.now() + 6 * DAY);
    assert.equal((await call(fns.requestAccountDeletion, {}, 'u1')).alreadyPending, true);
    assert.equal((await db.collection('notifications').where('userId', '==', 'u1').get()).size, 1);
    assert.equal((await call(fns.cancelAccountDeletion, {}, 'u1')).cancelled, true);
    await rejectsWithCode(call(fns.requestAccountDeletion, {}, null), 'unauthenticated');
  });
});

describe('processDueDeletions', () => {
  beforeEach(resetFirestore);

  it('leaves a request alone until its grace period has elapsed', async () => {
    await seedMember('u1');
    await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    const deps = makeDeps();
    const summary = await deletion.processDueDeletions(deps, NOW + DAY);
    assert.equal(summary.completed, 0);
    assert.ok(await exists('users/u1'));
    assert.equal(deps.deletedAuth.length, 0);
  });

  it('erases a member completely (health data too, when they did not opt into research) and leaves other members untouched', async () => {
    await seedMember('u1');
    await seedMember('u2');
    await db.doc('announcements/ann1').set({ title: 'Batch news', recipientUids: ['u1', 'u2', 'other'] });
    const { requestId } = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    const deps = makeDeps();
    const summary = await deletion.processDueDeletions(deps, NOW + 8 * DAY);
    assert.equal(summary.completed, 1);

    // gone: identity, subcollections, every per-user collection, health readings
    assert.equal(await exists('users/u1'), false);
    assert.equal(await exists('users/u1/announcements/a1'), false);
    assert.equal(await exists('users/u1/consentReceipts/c1'), false);
    for (const path of ['glucoseReadings/u1-1', 'bpReadings/u1-1', 'sleepLogs/u1-1', 'walkLogs/u1-1', 'weightLogs/u1-1', 'medicationLogs/u1-1', 'checklistLogs/u1-1', 'dailyCheckins/u1-1', 'labReports/u1-1', 'profiles/u1-p', 'notifications/u1-n', 'supportRequests/u1-s', 'errorReports/u1-e', 'programChatMessages/u1-m', 'programTypingStatus/progA_u1', 'coachInboxMessages/u1-i', 'coachMessages/u1-c', 'coachNotes/u1-n', 'programMembers/progA_u1', 'programInvites/u1-inv', 'programInvites/u1-used', 'batchStats/progA_2026-09-29/checkedInMembers/u1']) {
      assert.equal(await exists(path), false, `${path} should be deleted`);
    }
    // kept: other people's data, including their chat messages next to this member's
    assert.ok(await exists('users/u2'));
    assert.ok(await exists('glucoseReadings/u2-1'));
    assert.ok(await exists('programChatMessages/other-m-u1'));
    assert.ok(await exists('batchStats/progA_2026-09-29/checkedInMembers/u2'));
    assert.ok(await exists('coachNotes/u2-n'));

    // retained but de-identified: orders (financial), moderation reports, announcement audience list
    const order = (await db.doc('orders/u1-o').get()).data();
    assert.equal(order.total, 499);
    assert.equal(order.paymentStatus, 'paid');
    for (const field of ['userId', 'name', 'address', 'phone']) assert.equal(field in order, false, `order.${field} should be removed`);
    assert.ok(order.userIdHash);
    const asReporter = (await db.doc('reportedMessages/u1-r1').get()).data();
    assert.equal('reporterId' in asReporter, false);
    assert.equal(asReporter.reportedText, 'their words');
    const asReported = (await db.doc('reportedMessages/u1-r2').get()).data();
    assert.equal('reportedUserId' in asReported, false);
    assert.equal('reportedText' in asReported, false);
    assert.deepEqual((await db.doc('announcements/ann1').get()).get('recipientUids'), ['u2', 'other']);

    // storage + login
    for (const prefix of ['users/u1/', 'lab-reports/u1/', 'meal-photos/u1/', 'consultation-attachments/u1/', 'reports/u1/', 'program-chat-photos/progA/u1/', 'program-chat-audio/progA/u1/']) {
      assert.ok(deps.files.includes(prefix), `expected files under ${prefix} to be deleted`);
    }
    assert.deepEqual(deps.deletedAuth, ['u1']);

    // request closed out without keeping the uid, and an audit trail
    const request = (await db.doc(`deletionRequests/${requestId}`).get()).data();
    assert.equal(request.status, 'completed');
    assert.equal(request.mode, 'erased');
    assert.equal('userId' in request, false);
    assert.match(request.userIdHash, /^[0-9a-f]{64}$/);
    const audit = await db.collection('auditLogs').where('action', '==', 'account_deletion_completed').get();
    assert.equal(audit.size, 1);
    assert.equal(JSON.stringify(audit.docs[0].data()).includes('u1@example.com'), false);
  });

  it('keeps health readings, with no link back to the person, only when the member opted into research', async () => {
    await seedMember('u1', { research: true });
    await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    await deletion.processDueDeletions(makeDeps(), NOW + 8 * DAY);

    const glucose = (await db.doc('glucoseReadings/u1-1').get()).data();
    assert.equal(glucose.value, 100);
    assert.equal('userId' in glucose, false);
    assert.equal('profileId' in glucose, false);
    assert.ok(glucose.anonymizedAt);
    const lab = (await db.doc('labReports/u1-1').get()).data();
    assert.equal(lab.value, 6.1);
    for (const field of ['userId', 'labName', 'notes', 'fileUrl']) assert.equal(field in lab, false);
    assert.equal(await exists('users/u1'), false);
    assert.equal(await exists('profiles/u1-p'), false);
    const request = (await db.collection('deletionRequests').get()).docs[0].data();
    assert.equal(request.mode, 'anonymized_health_retained');
  });

  it('processes an admin-approved request immediately, and a stale "processing" one', async () => {
    await seedMember('u1');
    await seedMember('u2');
    const a = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    await db.doc(`deletionRequests/${a.requestId}`).set({ status: 'approved' }, { merge: true });
    const b = await deletion.scheduleAccountDeletion(db, 'u2', 'app', NOW);
    await db.doc(`deletionRequests/${b.requestId}`).set({ status: 'processing', updatedAt: Timestamp.fromMillis(NOW - 3 * 3600_000) }, { merge: true });
    const summary = await deletion.processDueDeletions(makeDeps(), NOW);
    assert.equal(summary.completed, 2);
    assert.equal(await exists('users/u1'), false);
    assert.equal(await exists('users/u2'), false);
  });

  it('does not touch a fresh "processing" request, or a cancelled/rejected one', async () => {
    await seedMember('u1');
    const a = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    await db.doc(`deletionRequests/${a.requestId}`).set({ status: 'processing', updatedAt: Timestamp.fromMillis(NOW - 60_000) }, { merge: true });
    assert.equal((await deletion.processDueDeletions(makeDeps(), NOW)).completed, 0);
    await db.doc(`deletionRequests/${a.requestId}`).set({ status: 'cancelled' }, { merge: true });
    assert.equal((await deletion.processDueDeletions(makeDeps(), NOW + 30 * DAY)).completed, 0);
    assert.ok(await exists('users/u1'));
  });

  it('retries after a failure, then gives up loudly after 5 attempts, and can be re-approved', async () => {
    await seedMember('u1');
    const { requestId } = await deletion.scheduleAccountDeletion(db, 'u1', 'app', NOW);
    const failing = makeDeps({ deleteAuthUser: async () => { throw new Error('auth backend unavailable'); } });
    for (let attempt = 1; attempt <= 4; attempt++) {
      const summary = await deletion.processDueDeletions(failing, NOW + 8 * DAY);
      assert.equal(summary.retrying, 1, `attempt ${attempt}`);
      const request = (await db.doc(`deletionRequests/${requestId}`).get()).data();
      assert.equal(request.status, 'approved');
      assert.equal(request.attempts, attempt);
      assert.match(request.lastError, /auth backend unavailable/);
    }
    assert.equal((await deletion.processDueDeletions(failing, NOW + 8 * DAY)).failed, 1);
    assert.equal((await db.doc(`deletionRequests/${requestId}`).get()).get('status'), 'failed');
    assert.equal((await deletion.processDueDeletions(failing, NOW + 8 * DAY)).completed, 0);

    // data was already erased on the earlier attempts; a healthy re-run completes the job (idempotent)
    await db.doc(`deletionRequests/${requestId}`).set({ status: 'approved', attempts: 0 }, { merge: true });
    const ok = makeDeps();
    assert.equal((await deletion.processDueDeletions(ok, NOW + 8 * DAY)).completed, 1);
    assert.deepEqual(ok.deletedAuth, ['u1']);
  });

  it('completes even when the member has nothing at all stored', async () => {
    const { requestId } = await deletion.scheduleAccountDeletion(db, 'ghost', 'app', NOW);
    assert.equal((await deletion.processDueDeletions(makeDeps(), NOW + 8 * DAY)).completed, 1);
    assert.equal((await db.doc(`deletionRequests/${requestId}`).get()).get('status'), 'completed');
  });
});

describe('adminManageDeletion', () => {
  beforeEach(async () => {
    await resetFirestore();
    await getAuth().createUser({ uid: 'member1', email: 'member1@example.com' }).catch(() => {});
    await getAuth().createUser({ uid: 'admin2', email: 'admin2@example.com' }).catch(() => {});
    await getAuth().setCustomUserClaims('admin2', { role: 'admin' });
  });

  it('is admin-only', async () => {
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'start', uid: 'member1' }, 'member1'), 'permission-denied');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'start', uid: 'member1' }, 'coach1', 'coach'), 'permission-denied');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'start', uid: 'member1' }, null), 'unauthenticated');
  });

  it('starts an emailed request by email lookup and approves it straight away', async () => {
    const res = await call(fns.adminManageDeletion, { action: 'start', email: 'member1@example.com' }, 'admin1', 'admin');
    assert.equal(res.status, 'approved');
    const request = (await db.doc(`deletionRequests/${res.requestId}`).get()).data();
    assert.equal(request.userId, 'member1');
    assert.equal(request.source, 'email');
    assert.equal(request.approvedBy, 'admin1');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'start', email: 'nobody@example.com' }, 'admin1', 'admin'), 'not-found');
  });

  it('protects admins and the caller themselves', async () => {
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'start', uid: 'admin1' }, 'admin1', 'admin'), 'failed-precondition');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'start', uid: 'admin2' }, 'admin1', 'admin'), 'failed-precondition');
  });

  it('approves or rejects a scheduled request, but not one that is already finished', async () => {
    const a = await deletion.scheduleAccountDeletion(db, 'member1', 'app', NOW);
    assert.equal((await call(fns.adminManageDeletion, { action: 'reject', requestId: a.requestId }, 'admin1', 'admin')).status, 'rejected');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'approve', requestId: a.requestId }, 'admin1', 'admin'), 'failed-precondition');
    const b = await deletion.scheduleAccountDeletion(db, 'member1', 'app', NOW);
    assert.equal((await call(fns.adminManageDeletion, { action: 'approve', requestId: b.requestId }, 'admin1', 'admin')).status, 'approved');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'approve', requestId: 'missing' }, 'admin1', 'admin'), 'not-found');
    await rejectsWithCode(call(fns.adminManageDeletion, { action: 'explode' }, 'admin1', 'admin'), 'invalid-argument');
  });
});
