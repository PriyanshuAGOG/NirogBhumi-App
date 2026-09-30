// The 60-minute correction window and its neighbours, enforced by Firestore rules with the SERVER clock
// (request.time). A member can fix a reading they entered for 60 minutes after it was created; after that
// it is part of their record. Imports from Health Connect are never hand-edited. Protected fields
// (userId, profileId, source, createdAt, providerRecordId) can never be changed by the member, and every
// correction leaves an audit trail (lastCorrectedAt, correctionCount).
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, increment, setDoc, serverTimestamp, Timestamp } from 'firebase/firestore';

let testEnv;
before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-nirog-bhumi',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: 'localhost', port: 8080 },
  });
});
after(async () => { await testEnv.cleanup(); });

const minutesAgo = (m) => Timestamp.fromMillis(Date.now() - m * 60_000);
const member = (uid) => testEnv.authenticatedContext(uid, { role: 'user' }).firestore();
const coach = (uid) => testEnv.authenticatedContext(uid, { role: 'coach' }).firestore();
const expert = (uid) => testEnv.authenticatedContext(uid, { role: 'expert' }).firestore();
const admin = () => testEnv.authenticatedContext('admin-uid', { role: 'admin' }).firestore();
const ts = serverTimestamp;

// A correction the way the app sends it (HealthRepository.updateHealthLog): changed fields + audit stamps, merged.
const correction = (fields) => ({ ...fields, updatedAt: ts(), lastCorrectedAt: ts(), correctionCount: increment(1) });

const manualGlucose = (createdAt) => ({ userId: 'mem', profileId: 'mem', value: 110, unit: 'mg/dL', readingType: 'fasting', measuredAt: createdAt, source: 'manual', createdAt });
const seed = (path, data) => testEnv.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'programs/progA'), { name: 'A', coachId: 'coach-a' });
    await setDoc(doc(db, 'users/mem'), { userId: 'mem', role: 'user', status: 'active', programActive: true, activeProgramId: 'progA' });
    await setDoc(doc(db, 'expertAssignments/expert-a_mem'), { expertId: 'expert-a', userId: 'mem' });
  });
});

describe('60-minute correction window', () => {
  it('lets a member correct their own entry inside the window', async () => {
    await seed('glucoseReadings/g1', manualGlucose(minutesAgo(5)));
    await assertSucceeds(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
  });

  it('still allows it at 59 minutes', async () => {
    await seed('glucoseReadings/g1', manualGlucose(minutesAgo(59)));
    await assertSucceeds(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
  });

  it('refuses it once 60 minutes have passed (server clock, not the phone)', async () => {
    await seed('glucoseReadings/g1', manualGlucose(minutesAgo(61)));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
  });

  it('refuses it days later', async () => {
    await seed('glucoseReadings/g1', manualGlucose(minutesAgo(60 * 24 * 3)));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
  });

  it('cannot be extended by sending a newer createdAt from a modified client', async () => {
    await seed('glucoseReadings/g1', manualGlucose(minutesAgo(120)));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118, createdAt: ts() }), { merge: true }));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118, createdAt: minutesAgo(1) }), { merge: true }));
  });

  it('does not depend on what the phone says the time is: a forged measuredAt does not reopen the window', async () => {
    await seed('glucoseReadings/g1', manualGlucose(minutesAgo(180)));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 118, measuredAt: Timestamp.now() }), { merge: true }));
  });

  it('works for every kind of reading a member can log', async () => {
    const cases = {
      bpReadings: [{ systolic: 120, diastolic: 80, source: 'manual' }, { systolic: 128, diastolic: 84 }],
      weightLogs: [{ valueKg: 70, source: 'manual' }, { valueKg: 69.5 }],
      walkLogs: [{ minutes: 20, activityType: 'walking', source: 'manual' }, { minutes: 25 }],
      medicationLogs: [{ taken: true, name: 'Metformin', source: 'manual' }, { taken: false }],
    };
    for (const [name, [base, edit]] of Object.entries(cases)) {
      await seed(`${name}/x1`, { userId: 'mem', profileId: 'mem', createdAt: minutesAgo(10), measuredAt: minutesAgo(10), ...base });
      await assertSucceeds(setDoc(doc(member('mem'), `${name}/x1`), correction(edit), { merge: true }));
      await seed(`${name}/old`, { userId: 'mem', profileId: 'mem', createdAt: minutesAgo(90), measuredAt: minutesAgo(90), ...base });
      await assertFails(setDoc(doc(member('mem'), `${name}/old`), correction(edit), { merge: true }));
    }
  });

  it('sleep: a coherent correction passes, an impossible one does not', async () => {
    const start = Timestamp.fromMillis(Date.now() - 9 * 3600_000);
    const end = Timestamp.fromMillis(Date.now() - 2 * 3600_000);
    await seed('sleepLogs/s1', { userId: 'mem', profileId: 'mem', createdAt: minutesAgo(10), source: 'manual', sleepStartAt: start, sleepEndAt: end, durationMinutes: 420, measuredAt: end });
    const newStart = Timestamp.fromMillis(Date.now() - 10 * 3600_000);
    await assertSucceeds(setDoc(doc(member('mem'), 'sleepLogs/s1'), correction({ sleepStartAt: newStart, durationMinutes: 480 }), { merge: true }));
    await seed('sleepLogs/s2', { userId: 'mem', profileId: 'mem', createdAt: minutesAgo(10), source: 'manual', sleepStartAt: start, sleepEndAt: end, durationMinutes: 420, measuredAt: end });
    await assertFails(setDoc(doc(member('mem'), 'sleepLogs/s2'), correction({ durationMinutes: 900 }), { merge: true })); // span is 7h
    await assertFails(setDoc(doc(member('mem'), 'sleepLogs/s2'), correction({ sleepEndAt: Timestamp.fromMillis(start.toMillis() - 1000), durationMinutes: 420 }), { merge: true }));
    await assertFails(setDoc(doc(member('mem'), 'sleepLogs/s2'), correction({ durationMinutes: 1200, sleepStartAt: Timestamp.fromMillis(end.toMillis() - 1200 * 60_000) }), { merge: true })); // > 18 h
  });
});

describe('protected fields and the audit trail', () => {
  beforeEach(() => seed('glucoseReadings/g1', manualGlucose(minutesAgo(5))));
  const edit = (fields) => assertFails(setDoc(doc(member('mem'), 'glucoseReadings/g1'), correction({ value: 120, ...fields }), { merge: true }));

  it('the member cannot change userId, profileId, source, createdAt or providerRecordId', async () => {
    await edit({ userId: 'someone-else' });
    await edit({ profileId: 'other-profile' });
    await edit({ source: 'health_connect' });
    await edit({ source: 'admin_correction' });
    await edit({ source: 'migration' });
    await edit({ createdAt: minutesAgo(400) });
    await edit({ providerRecordId: 'fake-provider-id' });
  });

  it('the member cannot sneak in arbitrary extra fields', async () => {
    await edit({ note: 'hello' });
    await edit({ status: 'reviewed' });
  });

  it('a correction must carry the audit trail: lastCorrectedAt = server time, correctionCount +1', async () => {
    const ref = doc(member('mem'), 'glucoseReadings/g1');
    await assertFails(setDoc(ref, { value: 120, updatedAt: ts() }, { merge: true }));                                            // no audit fields
    await assertFails(setDoc(ref, { value: 120, updatedAt: ts(), lastCorrectedAt: minutesAgo(1), correctionCount: increment(1) }, { merge: true })); // forged time
    await assertFails(setDoc(ref, { value: 120, updatedAt: ts(), lastCorrectedAt: ts(), correctionCount: increment(5) }, { merge: true }));          // count jumps
    await assertSucceeds(setDoc(ref, correction({ value: 120 }), { merge: true }));
    await assertSucceeds(setDoc(ref, correction({ value: 121 }), { merge: true }));                                              // second fix in the window counts 2
    let count;
    await testEnv.withSecurityRulesDisabled(async (ctx) => { count = (await getDoc(doc(ctx.firestore(), 'glucoseReadings/g1'))).data().correctionCount; });
    if (count !== 2) throw new Error(`expected correctionCount 2, got ${count}`);
  });

  it('values must stay sensible', async () => {
    await edit({ value: 5000 });
    await edit({ value: -3 });
    await edit({ value: 'abc' });
    await edit({ measuredAt: Timestamp.fromMillis(Date.now() + 3 * 3600_000) });   // in the future
    await edit({ measuredAt: Timestamp.fromMillis(Date.now() - 60 * 86400_000) }); // ancient
  });

  it('HbA1c stays on its own scale', async () => {
    await seed('glucoseReadings/h1', { userId: 'mem', profileId: 'mem', value: 6.5, unit: '%', readingType: 'hba1c', measuredAt: minutesAgo(5), source: 'manual', createdAt: minutesAgo(5) });
    const ref = doc(member('mem'), 'glucoseReadings/h1');
    await assertSucceeds(setDoc(ref, correction({ value: 6.9 }), { merge: true }));
    await assertFails(setDoc(ref, correction({ value: 110 }), { merge: true }));
  });
});

describe('Health Connect imports', () => {
  const imported = (extra = {}) => ({ userId: 'mem', profileId: 'mem', value: 98, unit: 'mg/dL', readingType: 'device', measuredAt: minutesAgo(600), source: 'health_connect', providerRecordId: 'rec-1', createdAt: minutesAgo(600), importedAt: ts(), ...extra });

  it('the sync can create an import with the provider time as createdAt, and re-sync it', async () => {
    const ref = doc(member('mem'), 'glucoseReadings/mem_glucose_rec-1');
    await assertSucceeds(setDoc(ref, imported(), { merge: true }));
    await assertSucceeds(setDoc(ref, imported({ value: 99, updatedAt: ts() }), { merge: true }));
  });

  it('an import is read-only as far as a member correction goes, even inside 60 minutes of arrival', async () => {
    await seed('glucoseReadings/i1', { ...imported(), createdAt: minutesAgo(3), importedAt: minutesAgo(3) });
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/i1'), correction({ value: 140 }), { merge: true }));
  });

  it('a manual entry cannot be converted into an import, and an import cannot become manual', async () => {
    await seed('glucoseReadings/m1', manualGlucose(minutesAgo(5)));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/m1'), { source: 'health_connect', providerRecordId: 'x', updatedAt: ts() }, { merge: true }));
    await seed('glucoseReadings/i1', imported());
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/i1'), { source: 'manual', updatedAt: ts() }, { merge: true }));
  });

  it('a re-sync cannot change the provider id, the owner or the profile', async () => {
    await seed('glucoseReadings/i1', imported());
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/i1'), imported({ providerRecordId: 'rec-2' }), { merge: true }));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/i1'), imported({ userId: 'other' }), { merge: true }));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/i1'), imported({ profileId: 'other' }), { merge: true }));
  });

  it('an import must name its provider record and cannot claim a future time', async () => {
    const { providerRecordId, ...withoutProvider } = imported();
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/n1'), withoutProvider));
    await assertFails(setDoc(doc(member('mem'), 'glucoseReadings/n2'), imported({ createdAt: Timestamp.fromMillis(Date.now() + 3 * 3600_000) })));
  });
});

describe('creating an entry', () => {
  it('needs the server timestamp, your own id and a manual source', async () => {
    const db = member('mem');
    await assertSucceeds(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', profileId: 'mem', value: 110, unit: 'mg/dL', readingType: 'fasting', measuredAt: ts(), source: 'manual', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', value: 110, source: 'manual', createdAt: minutesAgo(1) }));      // phone-chosen createdAt
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'other', value: 110, source: 'manual', createdAt: ts() }));              // someone else
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', value: 110, source: 'admin_correction', createdAt: ts() }));      // reserved sources
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', value: 110, source: 'migration', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', value: 110, source: 'manual', createdAt: ts(), correctionCount: 3 })); // forged audit
  });

  it('refuses impossible values and future measurement times', async () => {
    const db = member('mem');
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', value: 9999, source: 'manual', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'bpReadings')), { userId: 'mem', systolic: 400, diastolic: 80, source: 'manual', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'weightLogs')), { userId: 'mem', valueKg: 2, source: 'manual', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'sleepLogs')), { userId: 'mem', durationMinutes: 3000, source: 'manual', createdAt: ts() }));
    await assertFails(setDoc(doc(collection(db, 'glucoseReadings')), { userId: 'mem', value: 100, measuredAt: Timestamp.fromMillis(Date.now() + 5 * 3600_000), source: 'manual', createdAt: ts() }));
  });

  it('a tolerated clock drift (a few minutes) is fine', async () => {
    await assertSucceeds(setDoc(doc(collection(member('mem'), 'sleepLogs')), { userId: 'mem', sleepStartAt: minutesAgo(480), sleepEndAt: Timestamp.fromMillis(Date.now() + 4 * 60_000), durationMinutes: 484, measuredAt: Timestamp.fromMillis(Date.now() + 4 * 60_000), source: 'manual', createdAt: ts() }));
  });
});

describe('who else can touch a reading', () => {
  beforeEach(() => seed('glucoseReadings/g1', manualGlucose(minutesAgo(5))));

  it('another member cannot correct it', async () => {
    await assertFails(setDoc(doc(member('intruder'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
  });
  it('coaches and experts are read-only', async () => {
    await assertFails(setDoc(doc(coach('coach-a'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
    await assertFails(setDoc(doc(expert('expert-a'), 'glucoseReadings/g1'), correction({ value: 118 }), { merge: true }));
    await assertFails(deleteDoc(doc(coach('coach-a'), 'glucoseReadings/g1')));
  });
  it('an admin can fix an entry only with a named, timestamped correction, after the window too', async () => {
    await seed('glucoseReadings/old', manualGlucose(minutesAgo(60 * 48)));
    await assertFails(setDoc(doc(admin(), 'glucoseReadings/old'), { value: 100, updatedAt: ts() }, { merge: true }));  // silent edit
    await assertSucceeds(setDoc(doc(admin(), 'glucoseReadings/old'), { value: 100, lastCorrectedBy: 'admin-uid', lastCorrectedAt: ts(), updatedAt: ts() }, { merge: true }));
    await assertFails(setDoc(doc(admin(), 'glucoseReadings/old'), { value: 100, source: 'manual', userId: 'other', lastCorrectedBy: 'admin-uid', lastCorrectedAt: ts() }, { merge: true })); // cannot reassign the owner
    await assertFails(setDoc(doc(admin(), 'glucoseReadings/old'), { value: 100, lastCorrectedBy: 'someone-else', lastCorrectedAt: ts() }, { merge: true }));
  });
  it('the member can still delete their own reading (their data), other members cannot', async () => {
    await assertFails(deleteDoc(doc(member('intruder'), 'glucoseReadings/g1')));
    await assertSucceeds(deleteDoc(doc(member('mem'), 'glucoseReadings/g1')));
  });
});

describe('other record types are unchanged', () => {
  it('lab reports and checklist logs keep the simple owner rules', async () => {
    const db = member('mem');
    const lab = doc(collection(db, 'labReports'));
    await assertSucceeds(setDoc(lab, { userId: 'mem', profileId: 'mem', reportType: 'HbA1c', createdAt: ts() }));
    await assertSucceeds(setDoc(lab, { notes: 'edited later' }, { merge: true }));
    await assertFails(setDoc(lab, { userId: 'other' }, { merge: true }));
  });
});
