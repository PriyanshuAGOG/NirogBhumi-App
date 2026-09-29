const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { fns, db, sent, resetFirestore, trigger } = require('./helpers.cjs');

const fire = (id, data) => trigger(fns.onCoachInboxMessageCreate, id, data);

describe('onCoachInboxMessageCreate', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('programs/progA').set({ name: 'July Batch', coachId: 'coach-a' });
    await db.doc('users/member1').set({ fullName: 'Asha', fcmToken: 'member-token' });
    await db.doc('users/coach-a').set({ fullName: 'Coach A', fcmToken: 'coach-token' });
  });

  it('pushes a coach reply to the member and records it as sent', async () => {
    await fire('m1', { programId: 'progA', memberUid: 'member1', fromUid: 'coach-a', senderName: 'Coach A', text: 'Great progress this week!' });
    assert.equal(sent.length, 1);
    assert.equal(sent[0].token, 'member-token');
    assert.equal(sent[0].data.type, 'coach_message');
    assert.match(sent[0].notification.title, /Coach A replied/);
    const notes = await db.collection('notifications').where('userId', '==', 'member1').get();
    assert.equal(notes.size, 1);
    assert.equal(notes.docs[0].get('status'), 'sent');
  });

  it('notifies the program coach when a member asks a question', async () => {
    await fire('m2', { programId: 'progA', memberUid: 'member1', fromUid: 'member1', senderName: 'Asha', text: 'Can I walk after dinner?' });
    assert.equal(sent.length, 1);
    assert.equal(sent[0].token, 'coach-token');
    assert.match(sent[0].notification.title, /Asha asked a question/);
  });

  it('truncates long message bodies in the push', async () => {
    await fire('m3', { programId: 'progA', memberUid: 'member1', fromUid: 'coach-a', senderName: 'Coach A', text: 'x'.repeat(500) });
    assert.ok(sent[0].notification.body.length <= 140);
  });

  it('records missing_token instead of failing when the recipient has no device', async () => {
    await db.doc('users/member1').set({ fullName: 'Asha' });
    await fire('m4', { programId: 'progA', memberUid: 'member1', fromUid: 'coach-a', senderName: 'Coach A', text: 'Hello' });
    assert.equal(sent.length, 0);
    const notes = await db.collection('notifications').where('userId', '==', 'member1').get();
    assert.equal(notes.docs[0].get('failureReason'), 'missing_token');
  });

  it('records send_failed (and does not throw) when FCM rejects the token', async () => {
    await db.doc('users/member1').set({ fullName: 'Asha', fcmToken: 'bad-token' });
    await fire('m5', { programId: 'progA', memberUid: 'member1', fromUid: 'coach-a', senderName: 'Coach A', text: 'Hello' });
    const notes = await db.collection('notifications').where('userId', '==', 'member1').get();
    assert.equal(notes.docs[0].get('failureReason'), 'send_failed');
  });

  it('does nothing when the program has no coach, or the message is empty', async () => {
    await db.doc('programs/progB').set({ name: 'No coach' });
    await fire('m6', { programId: 'progB', memberUid: 'member1', fromUid: 'member1', senderName: 'Asha', text: 'Anyone there?' });
    await fire('m7', { programId: 'progA', memberUid: 'member1', fromUid: 'coach-a', senderName: 'Coach A', text: '   ' });
    assert.equal(sent.length, 0);
    assert.equal((await db.collection('notifications').get()).size, 0);
  });
});
