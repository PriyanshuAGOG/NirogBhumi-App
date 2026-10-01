const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('node:zlib');
const { Timestamp } = require('firebase-admin/firestore');
const { db, resetFirestore } = require('./helpers.cjs');
const { buildZip } = require('../lib/zip.js');
const { csvCell, toCsv, buildExportZip, plain } = require('../lib/dataExport.js');
const { processExportRequest, exportDownloadLink, purgeExpiredExports } = require('../lib/exportJob.js');
const { notifySupportRequest } = require('../lib/supportMail.js');
const { queueEmail } = require('../lib/email.js');

function readZip(buffer) {
  let end = buffer.length - 22;
  while (end >= 0 && buffer.readUInt32LE(end) !== 0x06054b50) end--;
  assert.ok(end >= 0, 'end of central directory');
  const count = buffer.readUInt16LE(end + 10);
  let pos = buffer.readUInt32LE(end + 16);
  const files = {};
  for (let i = 0; i < count; i++) {
    assert.equal(buffer.readUInt32LE(pos), 0x02014b50);
    const method = buffer.readUInt16LE(pos + 10), crc = buffer.readUInt32LE(pos + 16), size = buffer.readUInt32LE(pos + 20), usize = buffer.readUInt32LE(pos + 24);
    const nameLen = buffer.readUInt16LE(pos + 28), extraLen = buffer.readUInt16LE(pos + 30), commentLen = buffer.readUInt16LE(pos + 32), local = buffer.readUInt32LE(pos + 42);
    const name = buffer.toString('utf8', pos + 46, pos + 46 + nameLen);
    const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
    const body = buffer.subarray(start, start + size);
    const data = method === 8 ? zlib.inflateRawSync(body) : Buffer.from(body);
    assert.equal(data.length, usize, `size of ${name}`);
    assert.equal(zlib.crc32(data) >>> 0, crc, `crc of ${name}`);
    files[name] = data;
    pos += 46 + nameLen + extraLen + commentLen;
  }
  return files;
}

describe('zip writer', () => {
  it('round-trips text files (deflated and stored) with correct checksums', () => {
    const big = 'glucose,'.repeat(5000);
    const files = readZip(buildZip([{ name: 'a.txt', data: 'hi' }, { name: 'big.csv', data: big }, { name: 'é.txt'.replace('é', 'e'), data: Buffer.from([1, 2, 3]) }]));
    assert.equal(files['a.txt'].toString(), 'hi');
    assert.equal(files['big.csv'].toString(), big);
    assert.deepEqual([...files['e.txt']], [1, 2, 3]);
  });
  it('refuses names that could escape a folder', () => {
    for (const name of ['../x', '/etc/passwd', 'a/b.txt', 'a\\b.txt', '', '.hidden', 'x'.repeat(200)]) assert.throws(() => buildZip([{ name, data: 'x' }]), /unsafe zip entry name/, name);
  });
  it('an empty archive is still a valid zip', () => { assert.deepEqual(readZip(buildZip([])), {}); });
});

describe('csv', () => {
  it('escapes commas, quotes and line breaks', () => {
    assert.equal(csvCell('a,b'), '"a,b"');
    assert.equal(csvCell('say "hi"'), '"say ""hi"""');
    assert.equal(csvCell('line1\nline2'), '"line1\nline2"');
    assert.equal(csvCell(null), '');
    assert.equal(csvCell(undefined), '');
    assert.equal(csvCell(true), 'true');
  });
  it('defuses spreadsheet formulas but leaves plain negative numbers alone', () => {
    for (const evil of ['=HYPERLINK("http://x")', '+1+1', '-2+3', '@SUM(A1)', '\tcmd']) assert.ok(csvCell(evil).replace(/^"/, '').startsWith("'"), evil);
    assert.equal(csvCell('-12.5'), '-12.5');
    assert.equal(csvCell(-3), '-3');
  });
  it('writes timestamps as ISO text and keeps column order', () => {
    const csv = toCsv([{ id: 'r1', value: 110, measuredAt: Timestamp.fromMillis(Date.UTC(2026, 6, 1, 8, 30)) }], ['measuredAt', 'value', 'id']);
    assert.equal(csv, 'measuredAt,value,id\r\n2026-07-01T08:30:00.000Z,110,r1\r\n');
  });
  it('plain() converts nested timestamps', () => {
    assert.deepEqual(plain({ a: { t: Timestamp.fromMillis(0) }, b: [Timestamp.fromMillis(1000)] }), { a: { t: '1970-01-01T00:00:00.000Z' }, b: ['1970-01-01T00:00:01.000Z'] });
  });
  it('the export zip has data.json, every csv and a README with counts', () => {
    const { zip, counts } = buildExportZip({ exportedAt: '2026-07-01T00:00:00.000Z', glucoseReadings: [{ id: 'g1', value: 100, measuredAt: Timestamp.fromMillis(0) }], bpReadings: [] });
    const files = readZip(zip);
    assert.deepEqual(Object.keys(files).sort(), ['README.txt', 'activity.csv', 'blood_pressure.csv', 'blood_sugar.csv', 'data.json', 'medication.csv', 'sleep.csv', 'weight.csv']);
    assert.equal(counts.glucoseReadings, 1);
    assert.match(files['README.txt'].toString(), /glucoseReadings: 1/);
    assert.equal(JSON.parse(files['data.json']).glucoseReadings[0].measuredAt, '1970-01-01T00:00:00.000Z');
    assert.match(files['blood_sugar.csv'].toString(), /^measuredAt,value,unit,readingType,source,createdAt,id\r\n/);
  });
});

describe('export job', () => {
  const saved = [];
  const makeDeps = (over = {}) => ({
    db,
    saveFile: async (path, data, contentType, owner) => { saved.push({ path, data, contentType, owner }); },
    signUrl: async (path, ttl) => `https://signed.example/${encodeURIComponent(path)}?ttl=${ttl}`,
    now: () => new Date('2026-07-01T10:00:00Z'),
    ...over,
  });
  beforeEach(async () => {
    await resetFirestore(); saved.length = 0;
    await db.doc('users/u1').set({ userId: 'u1', fullName: 'Asha Verma', email: 'asha@example.com' });
    await db.doc('users/u2').set({ userId: 'u2', fullName: 'Phone Only', phone: '+911234567890' });
    await db.doc('glucoseReadings/g1').set({ userId: 'u1', value: 142, unit: 'mg/dL', readingType: 'fasting', createdAt: Timestamp.fromMillis(1_000) });
    await db.doc('glucoseReadings/other').set({ userId: 'someone-else', value: 999 });
    await db.doc('dataExportRequests/r1').set({ userId: 'u1', status: 'requested', createdAt: Timestamp.now() });
    await db.doc('dataExportRequests/r2').set({ userId: 'u2', status: 'requested', createdAt: Timestamp.now() });
  });

  it('builds a private zip of only this member\'s data and records where it is', async () => {
    assert.equal(await processExportRequest(makeDeps(), 'r1'), 'completed');
    assert.equal(saved.length, 1);
    assert.equal(saved[0].path, 'users/u1/exports/r1.zip'); assert.equal(saved[0].contentType, 'application/zip'); assert.equal(saved[0].owner, 'u1');
    const files = readZip(saved[0].data);
    const json = JSON.parse(files['data.json']);
    assert.equal(json.glucoseReadings.length, 1); assert.equal(json.glucoseReadings[0].id, 'g1');
    assert.ok(!files['data.json'].toString().includes('someone-else'));
    const req = (await db.doc('dataExportRequests/r1').get()).data();
    assert.equal(req.status, 'completed'); assert.equal(req.storagePath, 'users/u1/exports/r1.zip'); assert.equal(req.format, 'zip'); assert.equal(req.counts.glucoseReadings, 1);
  });

  it('emails a short-lived link once, with no health values in it', async () => {
    await processExportRequest(makeDeps(), 'r1');
    const mail = (await db.collection('mail').get()).docs;
    assert.equal(mail.length, 1); assert.equal(mail[0].id, 'export_r1');
    const m = mail[0].data();
    assert.deepEqual(m.to, ['asha@example.com']);
    assert.match(m.message.text, /https:\/\/signed\.example\/.*ttl=10800000/);
    assert.match(m.message.text, /3 hours/);
    assert.ok(!/142|Asha|Verma/.test(JSON.stringify(m.message)), 'no readings or name in the email');
    const req = (await db.doc('dataExportRequests/r1').get()).data();
    assert.equal(req.emailStatus, 'queued'); assert.equal(req.emailLinkIncluded, true);
  });

  it('a retried trigger does nothing more: no second file, no second email', async () => {
    await processExportRequest(makeDeps(), 'r1');
    assert.equal(await processExportRequest(makeDeps(), 'r1'), 'already_done');
    assert.equal(saved.length, 1);
    assert.equal((await db.collection('mail').get()).size, 1);
    // even if the request doc were reset and run again, the fixed mail key stops a second email
    await db.doc('dataExportRequests/r1').set({ status: 'requested' }, { merge: true });
    await processExportRequest(makeDeps(), 'r1');
    assert.equal((await db.collection('mail').get()).size, 1);
    assert.equal((await db.doc('dataExportRequests/r1').get()).get('emailStatus'), 'duplicate');
  });

  it('a phone-only member gets the file and an in-app notification, no email', async () => {
    await processExportRequest(makeDeps(), 'r2');
    assert.equal((await db.collection('mail').get()).size, 0);
    assert.equal((await db.doc('dataExportRequests/r2').get()).get('emailStatus'), 'skipped_no_email');
    assert.equal((await db.collection('notifications').where('userId', '==', 'u2').get()).size, 1);
    assert.equal((await db.doc('dataExportRequests/r2').get()).get('status'), 'completed');
  });

  it('when links cannot be signed the email points to the app instead of a link', async () => {
    await processExportRequest(makeDeps({ signUrl: async () => null }), 'r1');
    const m = (await db.doc('mail/export_r1').get()).data();
    assert.ok(!/https?:\/\//.test(m.message.text), 'no link in the email');
    assert.match(m.message.text, /Open the Nirog Bhumi app/);
    assert.equal((await db.doc('dataExportRequests/r1').get()).get('emailLinkIncluded'), false);
  });

  it('a storage failure marks the request failed (so the member can ask again) and is reported', async () => {
    await assert.rejects(() => processExportRequest(makeDeps({ saveFile: async () => { throw new Error('disk full'); } }), 'r1'), /disk full/);
    const req = (await db.doc('dataExportRequests/r1').get()).data();
    assert.equal(req.status, 'failed'); assert.equal(req.errorCode, 'export_failed');
    assert.equal((await db.collection('mail').get()).size, 0);
  });

  it('a missing request is ignored', async () => { assert.equal(await processExportRequest(makeDeps(), 'nope'), 'missing'); });

  it('download links are for the owner of a completed export only', async () => {
    await processExportRequest(makeDeps(), 'r1');
    const ok = await exportDownloadLink(makeDeps(), 'u1', 'r1');
    assert.match(ok.url, /ttl=900000/); assert.equal(ok.expiresInMinutes, 15); assert.equal(ok.storagePath, 'users/u1/exports/r1.zip');
    await assert.rejects(() => exportDownloadLink(makeDeps(), 'u2', 'r1'), { code: 'not-found' });
    await assert.rejects(() => exportDownloadLink(makeDeps(), 'u2', 'r2'), { code: 'not-found' }); // not completed yet
    await assert.rejects(() => exportDownloadLink(makeDeps(), 'u1', 'missing'), { code: 'not-found' });
    await db.doc('dataExportRequests/r1').set({ storagePath: 'users/u2/exports/r1.zip' }, { merge: true });
    await assert.rejects(() => exportDownloadLink(makeDeps(), 'u1', 'r1'), { code: 'not-found' }); // path tampering
  });
});

describe('export retention', () => {
  beforeEach(resetFirestore);
  const DAY = 86_400_000;
  it('deletes completed exports older than 30 days and marks them expired; leaves newer and unfinished ones', async () => {
    const now = new Date('2026-09-30T00:00:00Z');
    await db.doc('dataExportRequests/old').set({ userId: 'u1', status: 'completed', storagePath: 'users/u1/exports/old.zip', completedAt: Timestamp.fromMillis(now.getTime() - 31 * DAY) });
    await db.doc('dataExportRequests/fresh').set({ userId: 'u1', status: 'completed', storagePath: 'users/u1/exports/fresh.zip', completedAt: Timestamp.fromMillis(now.getTime() - 29 * DAY) });
    await db.doc('dataExportRequests/busy').set({ userId: 'u1', status: 'processing' });
    const deleted = [];
    const result = await purgeExpiredExports({ db, now: () => now, deleteFile: async p => { deleted.push(p); } });
    assert.deepEqual(result, { purged: 1, failed: 0 });
    assert.deepEqual(deleted, ['users/u1/exports/old.zip']);
    const old = (await db.doc('dataExportRequests/old').get()).data();
    assert.equal(old.status, 'expired'); assert.equal(old.storagePath, undefined);
    assert.equal((await db.doc('dataExportRequests/fresh').get()).get('status'), 'completed');
    assert.equal((await db.doc('dataExportRequests/busy').get()).get('status'), 'processing');
  });
  it('a storage failure is counted, not hidden, and does not loop forever', async () => {
    const now = new Date('2026-09-30T00:00:00Z');
    await db.doc('dataExportRequests/old').set({ userId: 'u1', status: 'completed', storagePath: 'users/u1/exports/old.zip', completedAt: Timestamp.fromMillis(now.getTime() - 40 * DAY) });
    const result = await purgeExpiredExports({ db, now: () => now, deleteFile: async () => { throw new Error('storage down'); } });
    assert.deepEqual(result, { purged: 0, failed: 1 });
    assert.equal((await db.doc('dataExportRequests/old').get()).get('status'), 'completed', 'left for the next run');
  });
  it('never deletes a path outside the member exports folder', async () => {
    const now = new Date('2026-09-30T00:00:00Z');
    await db.doc('dataExportRequests/odd').set({ userId: 'u1', status: 'completed', storagePath: 'users/u1/health-file/report.pdf', completedAt: Timestamp.fromMillis(now.getTime() - 40 * DAY) });
    const deleted = [];
    await purgeExpiredExports({ db, now: () => now, deleteFile: async p => { deleted.push(p); } });
    assert.deepEqual(deleted, []);
  });
});

describe('support email', () => {
  beforeEach(async () => {
    await resetFirestore();
    await db.doc('users/u1').set({ userId: 'u1', fullName: 'Asha\nBcc: evil@example.com', email: 'asha@example.com' });
  });
  const seedRequest = (id, data) => db.doc(`supportRequests/${id}`).set({ userId: 'u1', status: 'open', createdAt: Timestamp.now(), ...data });

  it('sends one sanitised email per request to the support inbox', async () => {
    await seedRequest('s1', { subject: 'Cannot log in\r\nBcc: attacker@example.com', message: 'Help <script>alert(1)</script>\u0000 please', appVersion: '1.0.3' });
    assert.equal(await notifySupportRequest(db, 's1', 'support@example.org'), 'queued');
    const mail = (await db.doc('mail/support_s1').get()).data();
    assert.deepEqual(mail.to, ['support@example.org']);
    assert.ok(!/[\r\n]/.test(mail.message.subject), 'subject is one line');
    assert.ok(!/\u0000/.test(mail.message.text));
    assert.ok(!mail.message.html.includes('<script>'), 'html is escaped');
    assert.match(mail.message.html, /&lt;script&gt;/);
    assert.ok(!/\r?\nBcc:/i.test(mail.message.text.split('\n').slice(0, 3).join('\n')), 'the name cannot inject a header line');
    assert.equal((await db.doc('supportRequests/s1').get()).get('emailStatus'), 'sent_to_inbox');
  });

  it('is idempotent for a retried trigger', async () => {
    await seedRequest('s1', { subject: 'Hi', message: 'Hello' });
    await notifySupportRequest(db, 's1', 'support@example.org');
    assert.equal(await notifySupportRequest(db, 's1', 'support@example.org'), 'duplicate');
    assert.equal((await db.collection('mail').get()).size, 1);
  });

  it('caps very long messages', async () => {
    await seedRequest('s1', { subject: 'x'.repeat(500), message: 'y'.repeat(20000) });
    await notifySupportRequest(db, 's1', 'support@example.org');
    const mail = (await db.doc('mail/support_s1').get()).data();
    assert.ok(mail.message.text.length < 4600); assert.ok(mail.message.subject.length <= 150);
  });

  it('does nothing harmful when no inbox is configured or the address is invalid', async () => {
    await seedRequest('s1', { subject: 'Hi', message: 'Hello' });
    assert.equal(await notifySupportRequest(db, 's1', undefined), 'skipped_not_configured');
    assert.equal(await notifySupportRequest(db, 's1', 'not an email'), 'skipped_not_configured');
    assert.equal((await db.collection('mail').get()).size, 0);
    assert.equal(await notifySupportRequest(db, 'missing', 'support@example.org'), 'skipped_missing');
  });

  it('queueEmail refuses a bad recipient', async () => {
    await assert.rejects(() => queueEmail(db, 'k', { to: 'a@b.c, d@e.f', subject: 's', text: 't' }), /invalid recipient/);
  });
});
