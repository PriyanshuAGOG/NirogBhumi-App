const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp } = require('firebase-admin/firestore');
const { db, sent, resetFirestore } = require('./helpers.cjs');
const { processPendingNotifications, mapPool } = require('../lib/notificationSender.js');
const { updateBatchPulse, sendWeeklyDigests, dayKeyIST } = require('../lib/scheduledJobs.js');

const HOUR = 3_600_000;
const deps = () => ({ db, send: async (m) => { if (m.token === 'bad-token') throw new Error('unregistered'); sent.push(m); } });
const due = () => Timestamp.fromMillis(Date.now() - 60_000);
const addNote = (data) => db.collection('notifications').add({ status: 'scheduled', scheduledFor: due(), title: 't', body: 'b', ...data });

// 12:00 IST - outside the default 21:00-07:00 quiet hours.
const NOON_IST = Date.parse('2026-09-29T06:30:00Z');
// 23:00 IST - inside quiet hours.
const NIGHT_IST = Date.parse('2026-09-29T17:30:00Z');

describe('processPendingNotifications', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('users/u1').set({ fcmToken: 'tok-1', timezone: 'Asia/Kolkata' });
    await db.doc('users/u2').set({ timezone: 'Asia/Kolkata' });
    await db.doc('users/u3').set({ fcmToken: 'bad-token', timezone: 'Asia/Kolkata' });
  });

  it('sends a due push and marks it sent; leaves future ones alone', async () => {
    const a = await addNote({ userId: 'u1', type: 'announcement' });
    const later = await addNote({ userId: 'u1', type: 'announcement', scheduledFor: Timestamp.fromMillis(Date.now() + HOUR) });
    const summary = await processPendingNotifications(deps());
    assert.equal(summary.sent, 1);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].token, 'tok-1');
    assert.equal(sent[0].data.type, 'announcement');
    assert.equal(sent[0].data.notificationId, a.id);
    assert.equal((await a.get()).get('status'), 'sent');
    assert.equal((await later.get()).get('status'), 'scheduled');
  });

  it('defers routine reminders in quiet hours, but never a coach message or critical alert', async () => {
    const reminder = await addNote({ userId: 'u1', type: 'reminder', scheduledFor: Timestamp.fromMillis(NIGHT_IST - 1000) });
    const human = await addNote({ userId: 'u1', type: 'coach_message', scheduledFor: Timestamp.fromMillis(NIGHT_IST - 1000) });
    const critical = await addNote({ userId: 'u1', type: 'critical_alert', scheduledFor: Timestamp.fromMillis(NIGHT_IST - 1000) });
    const summary = await processPendingNotifications(deps(), { now: () => NIGHT_IST });
    assert.equal(summary.deferred, 1);
    assert.equal(summary.sent, 2);
    const r = await reminder.get();
    assert.equal(r.get('status'), 'scheduled');
    assert.equal(r.get('deferredReason'), 'quiet_hours');
    assert.ok(r.get('scheduledFor').toMillis() > NIGHT_IST);
    assert.equal((await human.get()).get('status'), 'sent');
    assert.equal((await critical.get()).get('status'), 'sent');
  });

  it('honours the daily reminder cap', async () => {
    await db.doc('users/u1').set({ fcmToken: 'tok-1', timezone: 'Asia/Kolkata', notificationPreferences: { maxHealthReminders: 1 } });
    await db.collection('notifications').add({ userId: 'u1', type: 'reminder', status: 'sent', sentAt: Timestamp.fromMillis(NOON_IST - HOUR), title: 'x', body: 'y' });
    const pending = await addNote({ userId: 'u1', type: 'reminder', scheduledFor: Timestamp.fromMillis(NOON_IST - 1000) });
    const summary = await processPendingNotifications(deps(), { now: () => NOON_IST });
    assert.equal(summary.deferred, 1);
    assert.equal((await pending.get()).get('deferredReason'), 'daily_cap');
    assert.equal(sent.length, 0);
  });

  it('records missing tokens and rejected sends without stopping the batch', async () => {
    const noToken = await addNote({ userId: 'u2', type: 'announcement' });
    const rejected = await addNote({ userId: 'u3', type: 'announcement' });
    const ok = await addNote({ userId: 'u1', type: 'announcement' });
    const summary = await processPendingNotifications(deps());
    assert.deepEqual([summary.sent, summary.failed], [1, 2]);
    assert.equal((await noToken.get()).get('failureReason'), 'missing_token');
    assert.equal((await rejected.get()).get('failureReason'), 'send_failed');
    assert.equal((await ok.get()).get('status'), 'sent');
  });

  it('drains a large campaign in one run with bounded concurrency', async () => {
    const batch = db.batch();
    for (let i = 0; i < 300; i++) {
      batch.set(db.doc(`users/bulk${i}`), { fcmToken: `tok-${i}`, timezone: 'Asia/Kolkata' });
      batch.set(db.collection('notifications').doc(`n${i}`), { userId: `bulk${i}`, type: 'announcement', status: 'scheduled', scheduledFor: due(), title: 'Campaign', body: 'Hello' });
    }
    await batch.commit();
    let inFlight = 0; let peak = 0;
    const summary = await processPendingNotifications({ db, send: async (m) => { inFlight++; peak = Math.max(peak, inFlight); await new Promise((r) => setTimeout(r, 5)); inFlight--; sent.push(m); } }, { concurrency: 25 });
    assert.equal(summary.sent, 300);
    assert.equal(summary.stoppedEarly, false);
    assert.ok(peak > 1 && peak <= 25, `concurrency should be bounded, saw ${peak}`);
    assert.equal((await db.collection('notifications').where('status', '==', 'sent').get()).size, 300);
  });

  it('stops before its time budget runs out and leaves the rest scheduled', async () => {
    for (let i = 0; i < 10; i++) await addNote({ userId: 'u1', type: 'announcement' });
    let advance = 0; // each send "takes" 20ms of the fake clock
    const summary = await processPendingNotifications({ db, send: async (m) => { advance += 20; sent.push(m); } }, { concurrency: 1, budgetMs: 50, now: () => Date.now() + advance });
    assert.equal(summary.stoppedEarly, true);
    assert.ok(summary.sent < 10 && summary.sent >= 1);
    assert.equal((await db.collection('notifications').where('status', '==', 'scheduled').get()).size, 10 - summary.sent);
  });
});

describe('mapPool', () => {
  it('never exceeds the concurrency limit and processes everything', async () => {
    let inFlight = 0; let peak = 0; const seen = [];
    await mapPool([...Array(40).keys()], 4, async (i) => { inFlight++; peak = Math.max(peak, inFlight); await new Promise((r) => setTimeout(r, 2)); seen.push(i); inFlight--; });
    assert.equal(seen.length, 40);
    assert.ok(peak <= 4);
  });
});

describe('updateBatchPulse', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('programs/progA').set({ name: 'A' });
    await db.doc('programs/progEmpty').set({ name: 'Empty' });
    await db.doc('programMembers/progA_m1').set({ programId: 'progA', uid: 'm1', name: 'Asha', leaderboardOptIn: true });
    await db.doc('programMembers/progA_m2').set({ programId: 'progA', uid: 'm2', name: 'Ravi' });
  });

  it('sums this month\'s walking per batch and lists only members who opted in', async () => {
    await db.collection('walkLogs').add({ userId: 'm1', minutes: 30, createdAt: Timestamp.now() });
    await db.collection('walkLogs').add({ userId: 'm1', minutes: 20, createdAt: Timestamp.now() });
    await db.collection('walkLogs').add({ userId: 'm2', minutes: 45, createdAt: Timestamp.now() });
    await db.collection('walkLogs').add({ userId: 'm2', minutes: 999, createdAt: Timestamp.fromMillis(Date.now() - 90 * 86_400_000) });
    await db.collection('walkLogs').add({ userId: 'outsider', minutes: 500, createdAt: Timestamp.now() });
    const updated = await updateBatchPulse(db);
    assert.equal(updated, 1, 'empty batches are skipped');
    const stats = (await db.doc(`batchStats/progA_${dayKeyIST()}`).get()).data();
    assert.equal(stats.collectiveMinutes, 95);
    assert.equal(stats.memberCount, 2);
    assert.deepEqual(stats.leaderboard, [{ name: 'Asha', minutes: 50 }]);
  });
});

describe('sendWeeklyDigests', () => {
  beforeEach(resetFirestore);

  it('queues a nudge only for members who logged something, with the right wording', async () => {
    await db.doc('users/busy').set({ status: 'active' });
    await db.doc('users/light').set({ status: 'active' });
    await db.doc('users/idle').set({ status: 'active' });
    await db.doc('users/gone').set({ status: 'deleted' });
    for (let d = 1; d <= 5; d++) await db.collection('glucoseReadings').add({ userId: 'busy', value: 100, measuredAt: Timestamp.fromMillis(Date.now() - d * 86_400_000 + 3600_000) });
    await db.collection('sleepLogs').add({ userId: 'light', createdAt: Timestamp.fromMillis(Date.now() - 2 * 86_400_000) });
    await db.collection('glucoseReadings').add({ userId: 'gone', value: 100, measuredAt: Timestamp.now() });
    const summary = await sendWeeklyDigests(db);
    assert.equal(summary.usersScanned, 3);
    assert.equal(summary.digestsQueued, 2);
    const notes = await db.collection('notifications').where('type', '==', 'weekly_digest').get();
    const byUser = Object.fromEntries(notes.docs.map((d) => [d.get('userId'), d.get('body')]));
    assert.match(byUser.busy, /Great rhythm - you logged health data on 5 of the last 7 days/);
    assert.match(byUser.light, /on 1 of the last 7 days\. Try logging one thing/);
    assert.equal(byUser.idle, undefined);
    assert.ok(notes.docs[0].get('scheduledFor').toMillis() > Date.now() + 3 * HOUR, 'digest is scheduled for later that morning');
  });

  it('pages through more members than one page holds', async () => {
    const batch = db.batch();
    for (let i = 0; i < 25; i++) {
      batch.set(db.doc(`users/p${String(i).padStart(2, '0')}`), { status: 'active' });
      batch.set(db.collection('sleepLogs').doc(`s${i}`), { userId: `p${String(i).padStart(2, '0')}`, createdAt: Timestamp.now() });
    }
    await batch.commit();
    const summary = await sendWeeklyDigests(db, { pageSize: 10 });
    assert.equal(summary.usersScanned, 25);
    assert.equal(summary.digestsQueued, 25);
  });
});
