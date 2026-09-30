'use strict';
// Brings older health records up to the canonical shape (docs/HEALTH_DATA_SCHEMA.md) WITHOUT removing or
// rewriting anything the app already reads: fields are only ever added, `source` and `createdAt` are never
// touched, and the one destructive step (legacy per-interval step documents) needs its own explicit flag.
// Every change is idempotent: running it twice changes nothing the second time.

const DAY_MS = 86_400_000;
const MAX_SLEEP_MINUTES = 18 * 60;

const millisOf = (v) => {
  if (!v) return null;
  if (typeof v.toMillis === 'function') return v.toMillis();
  if (v instanceof Date) return v.getTime();
  if (typeof v._seconds === 'number') return v._seconds * 1000;
  return null;
};

/** `weightKg` (old Health Connect imports) gets a canonical `valueKg` twin. */
function planWeight(d) {
  if (typeof d.valueKg === 'number') return null;
  if (typeof d.weightKg === 'number' && Number.isFinite(d.weightKg)) return { valueKg: d.weightKg };
  return null;
}

/** Sleep with real start and wake instants (old imports: sleepTime/wakeTime) gains the canonical fields. Hours-only or clock-text entries cannot be reconstructed and are left to the tolerant readers. */
function planSleep(d) {
  if (d.sleepStartAt && d.sleepEndAt && typeof d.durationMinutes === 'number') return null;
  const start = millisOf(d.sleepStartAt ?? d.sleepTime);
  const end = millisOf(d.sleepEndAt ?? d.wakeTime);
  if (start === null || end === null || end <= start) return null;
  const minutes = Math.floor((end - start) / 60000);
  if (minutes < 1 || minutes > MAX_SLEEP_MINUTES) return null;
  const patch = {};
  if (!d.sleepStartAt) patch.sleepStartAt = new Date(start);
  if (!d.sleepEndAt) patch.sleepEndAt = new Date(end);
  if (typeof d.durationMinutes !== 'number') patch.durationMinutes = minutes;
  if (!d.measuredAt) patch.measuredAt = new Date(end);
  return Object.keys(patch).length ? patch : null;
}

/** A reading with no measuredAt is dated by when it was saved. */
function planMeasuredAt(d) {
  if (d.measuredAt) return null;
  const created = millisOf(d.createdAt);
  return created === null ? null : { measuredAt: new Date(created) };
}

const localDate = (millis, timeZone) => new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(millis));
const offsetMs = (instant, timeZone) => {
  // How far the wall clock in `timeZone` is ahead of UTC at `instant`.
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).formatToParts(new Date(instant)).map((p) => [p.type, p.value]));
  return Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second) - Math.floor(instant / 1000) * 1000;
};
/** The instant local midnight of `dateText` (YYYY-MM-DD) happens in `timeZone`; two passes settle zones whose offset changes that day. */
const startOfLocalDay = (dateText, timeZone) => {
  const wall = Date.parse(`${dateText}T00:00:00Z`);
  let instant = wall - offsetMs(wall, timeZone);
  instant = wall - offsetMs(instant, timeZone);
  return instant;
};

/** Old per-interval step documents (thousands a month) become one total per local day. */
function planStepDays(uid, docs, timeZone) {
  const days = new Map();
  const legacyIds = [];
  for (const { id, data } of docs) {
    if (data.source !== 'health_connect' || data.granularity === 'day' || !(data.steps > 0)) continue;
    const start = millisOf(data.startTime) ?? millisOf(data.measuredAt);
    const end = millisOf(data.endTime) ?? start;
    if (start === null) continue;
    const date = localDate(start, timeZone);
    const day = days.get(date) ?? { steps: 0, first: start, last: end };
    day.steps += Number(data.steps);
    day.first = Math.min(day.first, start); day.last = Math.max(day.last, end ?? start);
    days.set(date, day); legacyIds.push(id);
  }
  const create = [...days.entries()].map(([date, day]) => ({
    id: `${uid}_steps_day_${date}`,
    data: {
      userId: uid, profileId: uid, steps: day.steps, granularity: 'day', dayKey: date,
      startTime: new Date(day.first), endTime: new Date(day.last), measuredAt: new Date(day.last),
      createdAt: new Date(startOfLocalDay(date, timeZone)), source: 'health_connect', providerRecordId: `daily:${date}`,
    },
  }));
  return { create, legacyIds };
}

/** Refuses to touch anything that is not clearly a local emulator or a project the operator named AND approved. */
function assertSafeTarget({ project, emulatorHost, allowProduction }) {
  if (emulatorHost) return 'emulator';
  if (!project) throw new Error('Pass --project <id> (or run against the emulator with FIRESTORE_EMULATOR_HOST).');
  if (project.startsWith('demo-')) throw new Error('A demo- project only exists in the emulator: set FIRESTORE_EMULATOR_HOST.');
  if (!allowProduction) throw new Error(`"${project}" is a real project. Run against staging first; pass --allow-production only after a dry run was reviewed.`);
  return 'project';
}

const SIMPLE = {
  weightLogs: [planWeight, planMeasuredAt],
  sleepLogs: [planSleep, planMeasuredAt],
  glucoseReadings: [planMeasuredAt],
  bpReadings: [planMeasuredAt],
  medicationLogs: [planMeasuredAt],
};

/**
 * @param {{db: import('firebase-admin/firestore').Firestore, FieldValue: any, apply?: boolean, collections?: string[], limit?: number, deleteLegacySteps?: boolean, timeZone?: string, log?: (m: string) => void}} o
 */
async function runBackfill(o) {
  const { db, FieldValue, apply = false, limit = Infinity, deleteLegacySteps = false, log = () => {} } = o;
  const wanted = new Set(o.collections ?? [...Object.keys(SIMPLE), 'walkLogs']);
  const report = { mode: apply ? 'apply' : 'dry-run', collections: {} };

  for (const [name, planners] of Object.entries(SIMPLE)) {
    if (!wanted.has(name)) continue;
    const stats = { scanned: 0, changed: 0, fieldsAdded: {}, sampleIds: [] };
    report.collections[name] = stats;
    let last = null;
    while (stats.scanned < limit) {
      let q = db.collection(name).orderBy('__name__').limit(Math.min(400, limit - stats.scanned));
      if (last) q = q.startAfter(last);
      const snap = await q.get();
      if (snap.empty) break;
      const batch = db.batch();
      let writes = 0;
      for (const doc of snap.docs) {
        stats.scanned++;
        const data = doc.data();
        const patch = {};
        for (const plan of planners) Object.assign(patch, plan({ ...data, ...patch }) ?? {});
        if (!Object.keys(patch).length) continue;
        stats.changed++;
        for (const k of Object.keys(patch)) stats.fieldsAdded[k] = (stats.fieldsAdded[k] ?? 0) + 1;
        if (stats.sampleIds.length < 5) stats.sampleIds.push(doc.id);
        if (apply) { batch.set(doc.ref, { ...patch, schemaVersion: 2, backfilledAt: FieldValue.serverTimestamp() }, { merge: true }); writes++; }
      }
      if (apply && writes) await batch.commit();
      last = snap.docs[snap.docs.length - 1];
      log(`${name}: scanned ${stats.scanned}, would change ${stats.changed}`);
    }
  }

  if (wanted.has('walkLogs')) {
    const stats = { scanned: 0, legacyIntervalDocs: 0, dayTotalsToCreate: 0, deleted: 0, deleteSkippedWithoutFlag: 0 };
    report.collections.walkLogs = stats;
    const byUser = new Map();
    let last = null;
    while (stats.scanned < limit) {
      let q = db.collection('walkLogs').orderBy('__name__').limit(Math.min(400, limit - stats.scanned));
      if (last) q = q.startAfter(last);
      const snap = await q.get();
      if (snap.empty) break;
      for (const doc of snap.docs) {
        stats.scanned++;
        const d = doc.data();
        if (d.source === 'health_connect' && d.granularity !== 'day' && d.steps > 0 && d.userId) {
          const list = byUser.get(d.userId) ?? []; list.push({ id: doc.id, data: d }); byUser.set(d.userId, list);
        }
      }
      last = snap.docs[snap.docs.length - 1];
    }
    for (const [uid, docs] of byUser) {
      const zone = (await db.doc(`users/${uid}`).get()).get('timezone') || o.timeZone || 'Asia/Kolkata';
      const { create, legacyIds } = planStepDays(uid, docs, zone);
      stats.legacyIntervalDocs += legacyIds.length;
      stats.dayTotalsToCreate += create.length;
      if (!apply) continue;
      for (const c of create) await db.doc(`walkLogs/${c.id}`).set({ ...c.data, importedAt: FieldValue.serverTimestamp(), schemaVersion: 2, backfilledAt: FieldValue.serverTimestamp() }, { merge: true });
      if (deleteLegacySteps) {
        for (let i = 0; i < legacyIds.length; i += 400) {
          const batch = db.batch();
          legacyIds.slice(i, i + 400).forEach((id) => batch.delete(db.doc(`walkLogs/${id}`)));
          await batch.commit();
          stats.deleted += Math.min(400, legacyIds.length - i);
        }
      } else stats.deleteSkippedWithoutFlag += legacyIds.length;
    }
  }
  return report;
}

module.exports = { planWeight, planSleep, planMeasuredAt, planStepDays, assertSafeTarget, runBackfill, startOfLocalDay, localDate };
