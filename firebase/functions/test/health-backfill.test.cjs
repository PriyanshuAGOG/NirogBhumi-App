const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp, FieldValue } = require('firebase-admin/firestore');
const { db, resetFirestore } = require('./helpers.cjs');
const b = require('../../scripts/lib/healthBackfill.cjs');

const ts = (iso) => Timestamp.fromDate(new Date(iso));

describe('backfill planners', () => {
  it('weight: adds valueKg from weightKg, never the reverse, never twice', () => {
    assert.deepEqual(b.planWeight({ weightKg: 71.5 }), { valueKg: 71.5 });
    assert.equal(b.planWeight({ weightKg: 71.5, valueKg: 71.5 }), null);
    assert.equal(b.planWeight({ valueKg: 70 }), null);
    assert.equal(b.planWeight({ weightKg: 'heavy' }), null);
  });
  it('sleep: real start/wake instants gain the canonical fields', () => {
    const patch = b.planSleep({ sleepTime: ts('2026-07-01T16:30:00Z'), wakeTime: ts('2026-07-02T00:30:00Z'), duration: 8 });
    assert.equal(patch.durationMinutes, 480);
    assert.equal(patch.sleepStartAt.toISOString(), '2026-07-01T16:30:00.000Z');
    assert.equal(patch.measuredAt.toISOString(), '2026-07-02T00:30:00.000Z');
  });
  it('sleep: nothing to do when already canonical, and nothing invented when it cannot be known', () => {
    assert.equal(b.planSleep({ sleepStartAt: ts('2026-07-01T16:30:00Z'), sleepEndAt: ts('2026-07-02T00:30:00Z'), durationMinutes: 480, measuredAt: ts('2026-07-02T00:30:00Z') }), null);
    assert.equal(b.planSleep({ hours: 7 }), null);
    assert.equal(b.planSleep({ sleepTime: '22:30', wakeTime: '06:30' }), null);
    assert.equal(b.planSleep({ sleepTime: ts('2026-07-02T00:30:00Z'), wakeTime: ts('2026-07-01T16:30:00Z') }), null, 'wake before sleep');
    assert.equal(b.planSleep({ sleepTime: ts('2026-07-01T00:00:00Z'), wakeTime: ts('2026-07-02T06:00:00Z') }), null, 'over 18 hours stays as it is');
  });
  it('measuredAt falls back to createdAt only when missing', () => {
    assert.equal(b.planMeasuredAt({ measuredAt: ts('2026-07-01T00:00:00Z'), createdAt: ts('2026-07-05T00:00:00Z') }), null);
    assert.equal(b.planMeasuredAt({ createdAt: ts('2026-07-05T00:00:00Z') }).measuredAt.toISOString(), '2026-07-05T00:00:00.000Z');
    assert.equal(b.planMeasuredAt({}), null);
  });
  it('steps: intervals total per LOCAL day, not per UTC day', () => {
    // 23:30 IST on 1 July is 18:00 UTC on 1 July; 00:30 IST on 2 July is 19:00 UTC on 1 July -> two local days
    const docs = [
      { id: 'a', data: { source: 'health_connect', steps: 100, startTime: ts('2026-07-01T10:00:00Z'), endTime: ts('2026-07-01T10:10:00Z') } },
      { id: 'b', data: { source: 'health_connect', steps: 50, startTime: ts('2026-07-01T18:00:00Z'), endTime: ts('2026-07-01T18:05:00Z') } },
      { id: 'c', data: { source: 'health_connect', steps: 70, startTime: ts('2026-07-01T19:00:00Z'), endTime: ts('2026-07-01T19:05:00Z') } },
      { id: 'd', data: { source: 'manual', steps: 999 } },
      { id: 'e', data: { source: 'health_connect', granularity: 'day', steps: 5000 } },
    ];
    const { create, legacyIds } = b.planStepDays('u1', docs, 'Asia/Kolkata');
    assert.deepEqual(legacyIds.sort(), ['a', 'b', 'c']);
    const byId = Object.fromEntries(create.map((c) => [c.id, c.data]));
    assert.equal(byId['u1_steps_day_2026-07-01'].steps, 150);
    assert.equal(byId['u1_steps_day_2026-07-02'].steps, 70);
    assert.equal(byId['u1_steps_day_2026-07-01'].createdAt.toISOString(), '2026-06-30T18:30:00.000Z', 'local midnight of 1 July in IST');
    assert.equal(byId['u1_steps_day_2026-07-01'].granularity, 'day');
  });
  it('refuses unsafe targets', () => {
    assert.equal(b.assertSafeTarget({ emulatorHost: '127.0.0.1:8080' }), 'emulator');
    assert.throws(() => b.assertSafeTarget({}), /Pass --project/);
    assert.throws(() => b.assertSafeTarget({ project: 'demo-x' }), /emulator/);
    assert.throws(() => b.assertSafeTarget({ project: 'nirog-prod' }), /real project/);
    assert.equal(b.assertSafeTarget({ project: 'nirog-prod', allowProduction: true }), 'project');
  });
});

describe('backfill run (emulator)', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('users/u1').set({ userId: 'u1', timezone: 'Asia/Kolkata' });
    await db.doc('weightLogs/w1').set({ userId: 'u1', weightKg: 72, source: 'health_connect', createdAt: ts('2026-07-01T00:00:00Z') });
    await db.doc('weightLogs/w2').set({ userId: 'u1', valueKg: 70, source: 'manual', createdAt: ts('2026-07-01T00:00:00Z'), measuredAt: ts('2026-07-01T00:00:00Z') });
    await db.doc('sleepLogs/s1').set({ userId: 'u1', sleepTime: ts('2026-07-01T16:30:00Z'), wakeTime: ts('2026-07-02T00:30:00Z'), source: 'health_connect', createdAt: ts('2026-07-02T01:00:00Z') });
    await db.doc('glucoseReadings/g1').set({ userId: 'u1', value: 110, source: 'manual', createdAt: ts('2026-07-03T00:00:00Z') });
    await db.doc('walkLogs/k1').set({ userId: 'u1', source: 'health_connect', steps: 100, startTime: ts('2026-07-01T10:00:00Z'), endTime: ts('2026-07-01T10:10:00Z'), createdAt: ts('2026-07-01T10:00:00Z') });
    await db.doc('walkLogs/k2').set({ userId: 'u1', source: 'health_connect', steps: 50, startTime: ts('2026-07-01T11:00:00Z'), endTime: ts('2026-07-01T11:10:00Z'), createdAt: ts('2026-07-01T11:00:00Z') });
  });
  const run = (extra = {}) => b.runBackfill({ db, FieldValue, ...extra });

  it('a dry run reports what it would change and writes nothing', async () => {
    const report = await run();
    assert.equal(report.mode, 'dry-run');
    assert.equal(report.collections.weightLogs.changed, 1, 'only the old import changes; the canonical manual entry is left alone');
    assert.equal(report.collections.weightLogs.fieldsAdded.valueKg, 1);
    assert.equal(report.collections.sleepLogs.changed, 1);
    assert.equal(report.collections.glucoseReadings.fieldsAdded.measuredAt, 1);
    assert.equal(report.collections.walkLogs.legacyIntervalDocs, 2);
    assert.equal(report.collections.walkLogs.dayTotalsToCreate, 1);
    assert.equal((await db.doc('weightLogs/w1').get()).get('valueKg'), undefined);
    assert.equal((await db.doc('walkLogs/u1_steps_day_2026-07-01').get()).exists, false);
  });

  it('apply adds the canonical fields, keeps every original field, and is idempotent', async () => {
    await run({ apply: true });
    const w1 = (await db.doc('weightLogs/w1').get()).data();
    assert.equal(w1.valueKg, 72); assert.equal(w1.weightKg, 72); assert.equal(w1.source, 'health_connect'); assert.equal(w1.schemaVersion, 2);
    assert.ok(w1.measuredAt, 'measuredAt filled from createdAt');
    const s1 = (await db.doc('sleepLogs/s1').get()).data();
    assert.equal(s1.durationMinutes, 480); assert.ok(s1.sleepTime, 'the old fields stay');
    assert.equal((await db.doc('weightLogs/w2').get()).get('schemaVersion'), undefined, 'already canonical: untouched');
    const again = await run({ apply: true });
    for (const name of ['weightLogs', 'sleepLogs', 'glucoseReadings']) assert.equal(again.collections[name].changed, 0, `${name} second run`);
  });

  it('legacy step documents are kept unless the delete flag is given', async () => {
    const kept = await run({ apply: true });
    assert.equal(kept.collections.walkLogs.deleteSkippedWithoutFlag, 2);
    assert.equal((await db.doc('walkLogs/k1').get()).exists, true);
    const total = (await db.doc('walkLogs/u1_steps_day_2026-07-01').get()).data();
    assert.equal(total.steps, 150); assert.equal(total.userId, 'u1');
    const deleted = await run({ apply: true, deleteLegacySteps: true });
    assert.equal(deleted.collections.walkLogs.deleted, 2);
    assert.equal((await db.doc('walkLogs/k1').get()).exists, false);
    assert.equal((await db.doc('walkLogs/k2').get()).exists, false);
    assert.equal((await db.doc('walkLogs/u1_steps_day_2026-07-01').get()).get('steps'), 150, 'the daily total survives');
  });

  it('a canary limit stops early', async () => {
    const r = await run({ limit: 1, collections: ['weightLogs'] });
    assert.equal(r.collections.weightLogs.scanned, 1);
  });
});
