const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp } = require('firebase-admin/firestore');
const { fns, db, resetFirestore, call, rejectsWithCode } = require('./helpers.cjs');
const { handleConsultationChange } = require('../lib/consultations.js');

const HOUR = 3_600_000;
const inDays = (n) => Timestamp.fromMillis(Date.now() + n * 24 * HOUR);
const notes = async (id) => (await db.collection('notifications').where('consultationId', '==', id).get()).docs.map((d) => d.data());
const change = (id, before, after, now) => handleConsultationChange(db, id, before, after, now);

describe('handleConsultationChange', () => {
  beforeEach(resetFirestore);
  const pending = { userId: 'u1', consultationType: 'Diet review', status: 'pending', paymentStatus: 'pending' };

  it('confirming sends a confirmation push and queues a reminder an hour before', async () => {
    const at = inDays(3);
    await change('c1', pending, { ...pending, status: 'confirmed', scheduledAt: at, expertName: 'Dr. Meera' });
    const list = await notes('c1');
    const confirm = list.find((n) => n.kind === 'update');
    const reminder = list.find((n) => n.kind === 'reminder');
    assert.equal(confirm.title, 'Consultation confirmed');
    assert.match(confirm.body, /Diet review · .*with Dr\. Meera/);
    assert.equal(confirm.userId, 'u1');
    assert.equal(confirm.type, 'consultation');
    assert.equal(reminder.scheduledFor.toMillis(), at.toMillis() - HOUR);
    assert.equal(reminder.status, 'scheduled');
  });

  it('does not queue a reminder for a session that starts within the hour', async () => {
    await change('c2', pending, { ...pending, status: 'confirmed', scheduledAt: Timestamp.fromMillis(Date.now() + 30 * 60_000) });
    assert.equal((await notes('c2')).filter((n) => n.kind === 'reminder').length, 0);
  });

  it('rescheduling replaces the old reminder instead of leaving two', async () => {
    const first = { ...pending, status: 'confirmed', scheduledAt: inDays(2) };
    await change('c3', pending, first);
    const second = { ...first, scheduledAt: inDays(5) };
    await change('c3', first, second);
    const list = await notes('c3');
    const reminders = list.filter((n) => n.kind === 'reminder');
    assert.equal(reminders.length, 1);
    assert.equal(reminders[0].scheduledFor.toMillis(), second.scheduledAt.toMillis() - HOUR);
    assert.ok(list.some((n) => n.title === 'Consultation rescheduled'));
  });

  it('cancelling by staff removes the pending reminder and tells the member', async () => {
    const confirmed = { ...pending, status: 'confirmed', scheduledAt: inDays(2) };
    await change('c4', pending, confirmed);
    await change('c4', confirmed, { ...confirmed, status: 'cancelled' });
    const list = await notes('c4');
    assert.equal(list.filter((n) => n.kind === 'reminder').length, 0);
    assert.ok(list.some((n) => n.title === 'Your consultation was cancelled'));
  });

  it('a member-initiated cancel removes the reminder but does not push the member about their own action', async () => {
    const confirmed = { ...pending, status: 'confirmed', scheduledAt: inDays(2) };
    await change('c5', pending, confirmed);
    await change('c5', confirmed, { ...confirmed, status: 'cancelled', cancelledBy: 'member' });
    const list = await notes('c5');
    assert.equal(list.filter((n) => n.kind === 'reminder').length, 0);
    assert.equal(list.filter((n) => /cancelled/i.test(n.title)).length, 0);
  });

  it('declining sends the reason', async () => {
    await change('c6', pending, { ...pending, status: 'declined', declineReason: 'No diabetes educators free this week - try next Monday.' });
    const [n] = await notes('c6');
    assert.equal(n.title, "We couldn't schedule your consultation");
    assert.match(n.body, /try next Monday/);
  });

  it('ignores edits that change neither status nor time, and completing is silent', async () => {
    await change('c7', pending, { ...pending, concern: 'edited' });
    const confirmed = { ...pending, status: 'confirmed', scheduledAt: inDays(1) };
    await db.collection('notifications').doc('keep').set({ userId: 'u1', consultationId: 'c7', kind: 'reminder', status: 'sent' });
    await change('c7', confirmed, { ...confirmed, status: 'completed' });
    const list = await notes('c7');
    assert.equal(list.length, 1, 'only the pre-existing, already-sent record');
  });
});

describe('consultation callables and trigger', () => {
  beforeEach(resetFirestore);

  it('the update trigger runs the same logic', async () => {
    const at = inDays(2);
    const before = { userId: 'u1', consultationType: 'Yoga', status: 'pending' };
    await fns.onConsultationUpdate.run({ data: { before: { data: () => before }, after: { data: () => ({ ...before, status: 'confirmed', scheduledAt: at }) } }, params: { consultationId: 'ct1' } });
    assert.ok((await notes('ct1')).some((n) => n.title === 'Consultation confirmed'));
  });

  it('a member can cancel their own request; others cannot; a finished one cannot', async () => {
    await db.doc('consultations/k1').set({ userId: 'u1', status: 'pending' });
    await db.doc('consultations/k2').set({ userId: 'u1', status: 'completed' });
    await rejectsWithCode(call(fns.cancelConsultation, {}, 'u1'), 'invalid-argument');
    await rejectsWithCode(call(fns.cancelConsultation, { id: 'k1' }, 'intruder'), 'permission-denied');
    await rejectsWithCode(call(fns.cancelConsultation, { id: 'missing' }, 'u1'), 'not-found');
    await rejectsWithCode(call(fns.cancelConsultation, { id: 'k2' }, 'u1'), 'failed-precondition');
    await rejectsWithCode(call(fns.cancelConsultation, { id: 'k1' }, null), 'unauthenticated');
    assert.deepEqual(await call(fns.cancelConsultation, { id: 'k1' }, 'u1'), { cancelled: true, alreadyCancelled: false });
    const doc = (await db.doc('consultations/k1').get()).data();
    assert.equal(doc.status, 'cancelled');
    assert.equal(doc.cancelledBy, 'member');
    assert.deepEqual(await call(fns.cancelConsultation, { id: 'k1' }, 'u1'), { cancelled: false, alreadyCancelled: true });
  });
});
