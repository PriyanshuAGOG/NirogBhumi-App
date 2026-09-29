const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { fns, db, resetFirestore, trigger } = require('./helpers.cjs');

const fire = (id, data) => trigger(fns.onProgramResourceCreate, id, data);
const pushes = async () => (await db.collection('notifications').where('type', '==', 'program_resource').get()).docs.map((d) => d.data());

describe('onProgramResourceCreate', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('programMembers/progA_m1').set({ programId: 'progA', uid: 'm1', name: 'Asha' });
    await db.doc('programMembers/progA_m2').set({ programId: 'progA', uid: 'm2', name: 'Ravi' });
    await db.doc('programMembers/progA_coach').set({ programId: 'progA', uid: 'coach-a', name: 'Coach' });
    await db.doc('programMembers/progB_m9').set({ programId: 'progB', uid: 'm9', name: 'Other batch' });
  });

  it('queues one scheduled push per batch member, not the author or other batches', async () => {
    await fire('r1', { programId: 'progA', category: 'diet', title: 'Week 1 plate', createdBy: 'coach-a' });
    const list = await pushes();
    assert.deepEqual(list.map((n) => n.userId).sort(), ['m1', 'm2']);
    assert.ok(list.every((n) => n.status === 'scheduled' && /diet plan/.test(n.title) && n.body === 'Week 1 plate'));
  });

  it('stays silent when the author chose not to notify', async () => {
    await fire('r2', { programId: 'progA', category: 'yoga', title: 'Quiet update', notify: false, createdBy: 'coach-a' });
    assert.equal((await pushes()).length, 0);
  });

  it('handles a large batch in chunks and an empty batch gracefully', async () => {
    const batch = db.batch();
    for (let i = 0; i < 450; i++) batch.set(db.doc(`programMembers/big_${i}`), { programId: 'big', uid: `u${i}` });
    await batch.commit();
    await fire('r3', { programId: 'big', category: 'guidance', title: 'Hello everyone', createdBy: 'coach-x' });
    assert.equal((await pushes()).length, 450);
    await fire('r4', { programId: 'nobody-here', category: 'other', title: 'x', createdBy: 'c' });
  });
});
