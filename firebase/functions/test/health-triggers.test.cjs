const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp } = require('firebase-admin/firestore');
const { fns, db, resetFirestore } = require('./helpers.cjs');
const { dayKeyIST } = require('../lib/scheduledJobs.js');

const created = async (fn, path, data) => { await db.doc(path).set(data); await fn.run({ data: await db.doc(path).get(), params: { readingId: path.split('/')[1] } }); };
const updated = async (fn, path, before, after) => { await db.doc(path).set(after); await fn.run({ data: { before: { data: () => before }, after: { data: () => after, ref: db.doc(path) } }, params: { readingId: path.split('/')[1] } }); };
const get = async (path) => (await db.doc(path).get()).data();
const alerts = async () => (await db.collection('notifications').where('type', '==', 'critical_alert').get()).size;
const checkedIn = async () => (await db.doc(`batchStats/progA_${dayKeyIST()}`).get()).exists;
const glucose = (over) => ({ userId: 'u1', profileId: 'u1', value: 100, unit: 'mg/dL', readingType: 'fasting', source: 'manual', createdAt: Timestamp.now(), ...over });

describe('glucose categorisation (one table, same as the app)', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('users/u1').set({ userId: 'u1', programActive: true, activeProgramId: 'progA' });
    await db.doc('programMembers/progA_u1').set({ programId: 'progA', uid: 'u1' });
  });
  const statusOf = async (value, readingType) => { await created(fns.onGlucoseReadingCreate, `glucoseReadings/t${value}${readingType}`, glucose({ value, readingType })); return (await get(`glucoseReadings/t${value}${readingType}`)).status; };

  it('boundaries: 69 low, 70-130 fine, 131 high; after a meal fine up to 180; 53 and 300 critical', async () => {
    assert.equal(await statusOf(69, 'fasting'), 'needs_attention');
    assert.equal(await statusOf(70, 'fasting'), 'in_range');
    assert.equal(await statusOf(130, 'fasting'), 'in_range');
    assert.equal(await statusOf(131, 'fasting'), 'needs_attention');
    assert.equal(await statusOf(150, 'post_meal'), 'in_range');
    assert.equal(await statusOf(180, 'post_meal'), 'in_range');
    assert.equal(await statusOf(181, 'post_meal'), 'needs_attention');
    assert.equal(await statusOf(53, 'fasting'), 'critical');
    assert.equal(await statusOf(54, 'fasting'), 'needs_attention');
    assert.equal(await statusOf(299, 'fasting'), 'needs_attention');
    assert.equal(await statusOf(300, 'fasting'), 'critical');
  });

  it('HbA1c is a percentage and is never classified as mg/dL', async () => {
    assert.equal(await statusOf(6.5, 'hba1c'), 'in_range');
  });

  it('only a fasting reading is mirrored as the fasting sugar, and only a real check-in counts', async () => {
    await created(fns.onGlucoseReadingCreate, 'glucoseReadings/a', glucose({ value: 150, readingType: 'post_meal' }));
    assert.equal((await get('users/u1')).latestMetrics, undefined, 'after-meal is not "fasting sugar"');
    await created(fns.onGlucoseReadingCreate, 'glucoseReadings/b', glucose({ value: 110 }));
    assert.equal((await get('users/u1')).latestMetrics.fastingSugar, 110);
    assert.equal(await checkedIn(), true);
  });

  it('imports and lab values are not check-ins and never raise a critical push', async () => {
    await created(fns.onGlucoseReadingCreate, 'glucoseReadings/hc', glucose({ value: 320, readingType: 'device', source: 'health_connect', providerRecordId: 'r1' }));
    assert.equal(await alerts(), 0);
    assert.equal(await checkedIn(), false);
    await created(fns.onGlucoseReadingCreate, 'glucoseReadings/lab', glucose({ value: 6.8, readingType: 'hba1c' }));
    assert.equal(await checkedIn(), false);
    await created(fns.onGlucoseReadingCreate, 'glucoseReadings/manual', glucose({ value: 320 }));
    assert.equal(await alerts(), 1);
    assert.equal(await checkedIn(), true);
  });

  it('correcting a critical reading clears the flag and the mirrored value, and a new critical value alerts once', async () => {
    const before = glucose({ value: 320 });
    await created(fns.onGlucoseReadingCreate, 'glucoseReadings/c1', before);
    assert.equal((await get('glucoseReadings/c1')).status, 'critical');
    assert.equal(await alerts(), 1);
    const fixed = { ...(await get('glucoseReadings/c1')), value: 130 };
    await updated(fns.onGlucoseReadingUpdate, 'glucoseReadings/c1', { ...(await get('glucoseReadings/c1')) }, fixed);
    assert.equal((await get('glucoseReadings/c1')).status, 'in_range');
    assert.equal((await get('users/u1')).latestMetrics.fastingSugar, 130, 'the latest reading was the one corrected');
    const again = { ...fixed, value: 310 };
    await updated(fns.onGlucoseReadingUpdate, 'glucoseReadings/c1', { ...fixed, status: 'in_range' }, again);
    assert.equal((await get('glucoseReadings/c1')).status, 'critical');
    assert.equal(await alerts(), 2);
  });

  it('the server\'s own status write does not loop', async () => {
    const doc = glucose({ value: 100, status: 'in_range' });
    await updated(fns.onGlucoseReadingUpdate, 'glucoseReadings/same', doc, { ...doc, status: 'in_range', categorizedAt: Timestamp.now() });
    assert.equal(await alerts(), 0);
  });
});

describe('blood pressure categorisation', () => {
  beforeEach(resetFirestore);
  it('critical at 180/120, corrected down clears it, imports never alert', async () => {
    await created(fns.onBPReadingCreate, 'bpReadings/p1', { userId: 'u1', profileId: 'u1', systolic: 185, diastolic: 100, source: 'manual', createdAt: Timestamp.now() });
    assert.equal((await get('bpReadings/p1')).status, 'critical');
    assert.equal(await alerts(), 1);
    const now = await get('bpReadings/p1');
    await updated(fns.onBPReadingUpdate, 'bpReadings/p1', now, { ...now, systolic: 125 });
    assert.equal((await get('bpReadings/p1')).status, 'recorded');
    await created(fns.onBPReadingCreate, 'bpReadings/hc', { userId: 'u1', profileId: 'u1', systolic: 190, diastolic: 100, source: 'health_connect', providerRecordId: 'x', createdAt: Timestamp.now() });
    assert.equal(await alerts(), 1, 'an imported value does not page the member or the coach');
  });
});
