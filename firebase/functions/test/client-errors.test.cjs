// reportClientError is open to signed-out callers on purpose. These tests prove what it keeps, what it refuses and how it is throttled.
const { test, before } = require('node:test');
const assert = require('node:assert/strict');
const { fns, db, resetFirestore, call, rejectsWithCode } = require('./helpers.cjs');

before(resetFirestore);

const base = { screen: 'sign_in:phone_send', message: 'We could not confirm that this app installation is genuine', code: 'ERROR_X', detail: 'FirebaseException ERROR_X', appVersion: '1.0.250', versionCode: 250, device: 'Samsung SM-A515F', androidSdk: 34, installId: 'inst-aaa' };
const reports = async () => (await db.collection('errorReports').get()).docs;

test('a signed-out caller can report a problem, and it lands as an open report', async () => {
  const result = await call(fns.reportClientError, base, null);
  assert.deepEqual(result, { accepted: true });
  const docs = await reports();
  assert.equal(docs.length, 1);
  const d = docs[0].data();
  assert.equal(d.userId, null);
  assert.equal(d.signedIn, false);
  assert.equal(d.resolved, false);
  assert.equal(d.screen, 'sign_in:phone_send');
  assert.equal(d.source, 'app_client');
  assert.equal(d.count, 1);
  assert.deepEqual(d.installs, ['inst-aaa']);
});

test('the same problem again is one row with a higher count, and reopens a resolved one', async () => {
  await db.doc((await reports())[0].ref.path).update({ resolved: true });
  await call(fns.reportClientError, { ...base, installId: 'inst-bbb' }, null);
  const docs = await reports();
  assert.equal(docs.length, 1);
  assert.equal(docs[0].get('count'), 2);
  assert.equal(docs[0].get('resolved'), false);
  assert.deepEqual(docs[0].get('installs').sort(), ['inst-aaa', 'inst-bbb']);
});

test('phone numbers and email addresses are removed before anything is stored', async () => {
  await resetFirestore();
  await call(fns.reportClientError, { ...base, message: 'Failed for +919876543210 and asha@example.org', detail: 'number 9876543210 blocked' }, null);
  const text = JSON.stringify((await reports())[0].data());
  assert.ok(!text.includes('9876543210'), text);
  assert.ok(!text.includes('asha@example.org'), text);
  assert.ok(text.includes('[number]') && text.includes('[email]'), text);
});

test('the signed-in account comes from the platform, never from the payload', async () => {
  await resetFirestore();
  await call(fns.reportClientError, { ...base, userId: 'someone-else', uid: 'someone-else' }, 'real-user');
  const d = (await reports())[0].data();
  assert.equal(d.userId, 'real-user');
  assert.equal(d.signedIn, true);
});

test('missing or empty fields are refused, and oversized text is clipped', async () => {
  await resetFirestore();
  await rejectsWithCode(call(fns.reportClientError, {}, null), 'invalid-argument');
  await rejectsWithCode(call(fns.reportClientError, { screen: 'x' }, null), 'invalid-argument');
  await rejectsWithCode(call(fns.reportClientError, { screen: '   ', message: 'm' }, null), 'invalid-argument');
  await call(fns.reportClientError, { ...base, message: 'm'.repeat(5000), detail: 'd'.repeat(5000), device: 'x'.repeat(500) }, null);
  const d = (await reports())[0].data();
  assert.equal(d.message.length, 500);
  assert.equal(d.detail.length, 300);
  assert.equal(d.device.length, 60);
});

test('one installation cannot flood the dashboard: the excess is quietly dropped', async () => {
  await resetFirestore();
  let accepted = 0;
  for (let i = 0; i < 30; i++) {
    const r = await call(fns.reportClientError, { ...base, screen: `s${i}`, installId: 'flooder' }, null);
    if (r.accepted) accepted++;
  }
  assert.equal(accepted, 20);
  assert.equal((await reports()).length, 20);
});

test('only an admin can read the reports (the rules, not this function, decide that)', async () => {
  // The function writes with the server's own access; reading is governed by firestore.rules (errorReports: read if admin()),
  // which firebase/rules-tests proves. Nothing here hands a report back to the caller beyond { accepted }.
  const result = await call(fns.reportClientError, { ...base, screen: 'again', installId: 'inst-ccc' }, null);
  assert.deepEqual(Object.keys(result), ['accepted']);
});
